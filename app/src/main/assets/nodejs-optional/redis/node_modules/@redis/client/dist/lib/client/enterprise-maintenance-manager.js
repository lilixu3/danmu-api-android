"use strict";
var __importDefault = (this && this.__importDefault) || function (mod) {
    return (mod && mod.__esModule) ? mod : { "default": mod };
};
var _a;
Object.defineProperty(exports, "__esModule", { value: true });
exports.emitDiagnostics = exports.dbgMaintenance = exports.MAINTENANCE_EVENTS = exports.SMIGRATED_EVENT = void 0;
const net_1 = require("net");
const promises_1 = require("dns/promises");
const node_assert_1 = __importDefault(require("node:assert"));
const promises_2 = require("node:timers/promises");
const node_diagnostics_channel_1 = __importDefault(require("node:diagnostics_channel"));
exports.SMIGRATED_EVENT = "__SMIGRATED";
exports.MAINTENANCE_EVENTS = {
    PAUSE_WRITING: "pause-writing",
    RESUME_WRITING: "resume-writing",
    TIMEOUTS_UPDATE: "timeouts-update",
};
const PN = {
    MOVING: "MOVING",
    MIGRATING: "MIGRATING",
    MIGRATED: "MIGRATED",
    FAILING_OVER: "FAILING_OVER",
    FAILED_OVER: "FAILED_OVER",
    SMIGRATING: "SMIGRATING",
    SMIGRATED: "SMIGRATED",
};
const dbgMaintenance = (...args) => {
    if (!process.env.REDIS_DEBUG_MAINTENANCE)
        return;
    return console.log(new Date().toISOString().slice(11, 23), "[MNT]", ...args);
};
exports.dbgMaintenance = dbgMaintenance;
const emitDiagnostics = (event) => {
    if (!process.env.REDIS_EMIT_DIAGNOSTICS)
        return;
    const channel = node_diagnostics_channel_1.default.channel("redis.maintenance");
    channel.publish(event);
};
exports.emitDiagnostics = emitDiagnostics;
class EnterpriseMaintenanceManager {
    #commandsQueue;
    #options;
    #isMaintenance = 0;
    #client;
    static setupDefaultMaintOptions(options) {
        if (options.maintNotifications === undefined) {
            options.maintNotifications =
                options?.RESP === 3 ? "auto" : "disabled";
        }
        if (options.maintEndpointType === undefined) {
            options.maintEndpointType = "auto";
        }
        if (options.maintRelaxedSocketTimeout === undefined) {
            options.maintRelaxedSocketTimeout = 10000;
        }
        if (options.maintRelaxedCommandTimeout === undefined) {
            options.maintRelaxedCommandTimeout = 10000;
        }
    }
    static async getHandshakeCommand(options) {
        if (options.maintNotifications === "disabled")
            return;
        const host = options.url
            ? new URL(options.url).hostname
            : options.socket?.host;
        if (!host)
            return;
        const tls = options.socket?.tls ?? false;
        const movingEndpointType = await determineEndpoint(tls, host, options);
        return {
            cmd: [
                "CLIENT",
                "MAINT_NOTIFICATIONS",
                "ON",
                "moving-endpoint-type",
                movingEndpointType,
            ],
            errorHandler: (error) => {
                (0, exports.dbgMaintenance)("handshake failed:", error);
                if (options.maintNotifications === "enabled") {
                    throw error;
                }
            },
        };
    }
    constructor(commandsQueue, client, options) {
        this.#commandsQueue = commandsQueue;
        this.#options = options;
        this.#client = client;
        this.#commandsQueue.addPushHandler(this.#onPush);
    }
    #onPush = (push) => {
        (0, exports.dbgMaintenance)("ONPUSH:", push.map(String));
        if (!Array.isArray(push) || !Object.values(PN).includes(String(push[0]))) {
            return false;
        }
        const type = String(push[0]);
        (0, exports.emitDiagnostics)({
            type,
            timestamp: Date.now(),
            data: {
                push: push.map(String),
            },
        });
        switch (type) {
            case PN.MOVING: {
                // [ 'MOVING', '17', '15', '54.78.247.156:12075' ]
                //             ^seq   ^after    ^new ip
                const afterSeconds = push[2];
                const url = push[3] ? String(push[3]) : null;
                (0, exports.dbgMaintenance)("Received MOVING:", afterSeconds, url);
                this.#onMoving(afterSeconds, url);
                return true;
            }
            case PN.MIGRATING:
            case PN.SMIGRATING:
            case PN.FAILING_OVER: {
                (0, exports.dbgMaintenance)("Received MIGRATING|SMIGRATING|FAILING_OVER");
                this.#onMigrating();
                return true;
            }
            case PN.MIGRATED:
            case PN.FAILED_OVER: {
                (0, exports.dbgMaintenance)("Received MIGRATED|FAILED_OVER");
                this.#onMigrated();
                return true;
            }
            case PN.SMIGRATED: {
                (0, exports.dbgMaintenance)("Received SMIGRATED");
                this.#onSMigrated(push);
                this.#onMigrated(); // Un-relax timeouts after slot migration completes
                return true;
            }
        }
        return false;
    };
    //  Queue:
    //     toWrite [ C D E ]
    //     waitingForReply [ A B ]   - aka In-flight commands
    //
    //  time: ---1-2---3-4-5-6---------------------------
    //
    //  1. [EVENT] MOVING PN received
    //  2. [ACTION] Pause writing ( we need to wait for new socket to connect and for all in-flight commands to complete )
    //  3. [EVENT] New socket connected
    //  4. [EVENT] In-flight commands completed
    //  5. [ACTION] Destroy old socket
    //  6. [ACTION] Resume writing -> we are going to write to the new socket from now on
    #onMoving = async (afterSeconds, url) => {
        // 1 [EVENT] MOVING PN received
        this.#onMigrating();
        let host;
        let port;
        // The special value `none` indicates that the `MOVING` message doesn’t need
        // to contain an endpoint. Instead it contains the value `null` then. In
        // such a corner case, the client is expected to schedule a graceful
        // reconnect to its currently configured endpoint after half of the grace
        // period that was communicated by the server is over.
        if (url === null) {
            (0, node_assert_1.default)(this.#options.maintEndpointType === "none");
            const { host: h, port: p } = this.#getAddress();
            host = h;
            port = p;
            const waitTime = (afterSeconds * 1000) / 2;
            (0, exports.dbgMaintenance)(`Wait for ${waitTime}ms`);
            await (0, promises_2.setTimeout)(waitTime);
        }
        else {
            const split = url.split(":");
            host = split[0];
            port = Number(split[1]);
        }
        // 2 [ACTION] Pause writing
        (0, exports.dbgMaintenance)("Pausing writing of new commands to old socket");
        this.#client._pause();
        (0, exports.dbgMaintenance)("Creating new tmp client");
        let start = performance.now();
        // If the URL is provided, it takes precedense
        // the options object could just be mutated
        if (this.#options.url) {
            const u = new URL(this.#options.url);
            u.hostname = host;
            u.port = String(port);
            this.#options.url = u.toString();
        }
        else {
            this.#options.socket = {
                ...this.#options.socket,
                host,
                port,
            };
        }
        const tmpClient = this.#client.duplicate();
        tmpClient.on("error", (error) => {
            //We dont know how to handle tmp client errors
            (0, exports.dbgMaintenance)(`[ERR]`, error);
        });
        (0, exports.dbgMaintenance)(`Tmp client created in ${(performance.now() - start).toFixed(2)}ms`);
        (0, exports.dbgMaintenance)(`Set timeout for tmp client to ${this.#options.maintRelaxedSocketTimeout}`);
        tmpClient._maintenanceUpdate({
            relaxedCommandTimeout: this.#options.maintRelaxedCommandTimeout,
            relaxedSocketTimeout: this.#options.maintRelaxedSocketTimeout,
        });
        (0, exports.dbgMaintenance)(`Connecting tmp client: ${host}:${port}`);
        start = performance.now();
        await tmpClient.connect();
        (0, exports.dbgMaintenance)(`Connected to tmp client in ${(performance.now() - start).toFixed(2)}ms`);
        // 3 [EVENT] New socket connected
        (0, exports.dbgMaintenance)(`Wait for all in-flight commands to complete`);
        await this.#commandsQueue.waitForInflightCommandsToComplete();
        (0, exports.dbgMaintenance)(`In-flight commands completed`);
        // 4 [EVENT] In-flight commands completed
        (0, exports.dbgMaintenance)("Swap client sockets...");
        const oldSocket = this.#client._ejectSocket();
        const newSocket = tmpClient._ejectSocket();
        this.#client._insertSocket(newSocket);
        tmpClient._insertSocket(oldSocket);
        tmpClient.destroy();
        (0, exports.dbgMaintenance)("Swap client sockets done.");
        // 5 + 6
        (0, exports.dbgMaintenance)("Resume writing");
        this.#client._unpause();
        this.#onMigrated();
    };
    #onMigrating = () => {
        this.#isMaintenance++;
        if (this.#isMaintenance > 1) {
            (0, exports.dbgMaintenance)(`Timeout relaxation already done`);
            return;
        }
        const update = {
            relaxedCommandTimeout: this.#options.maintRelaxedCommandTimeout,
            relaxedSocketTimeout: this.#options.maintRelaxedSocketTimeout,
        };
        this.#client._maintenanceUpdate(update);
    };
    #onMigrated = () => {
        //ensure that #isMaintenance doesnt go under 0
        this.#isMaintenance = Math.max(this.#isMaintenance - 1, 0);
        if (this.#isMaintenance > 0) {
            (0, exports.dbgMaintenance)(`Not ready to unrelax timeouts yet`);
            return;
        }
        const update = {
            relaxedCommandTimeout: undefined,
            relaxedSocketTimeout: undefined,
        };
        this.#client._maintenanceUpdate(update);
    };
    #onSMigrated = (push) => {
        const smigratedEvent = _a.parseSMigratedPush(push);
        (0, exports.dbgMaintenance)(`emit smigratedEvent`, smigratedEvent);
        this.#client._handleSmigrated(smigratedEvent);
    };
    /**
     * Parses an SMIGRATED push message into a structured SMigratedEvent.
     *
     * SMIGRATED format:
     * - SMIGRATED, "seqid", followed by a list of N triplets:
     *     - source endpoint
     *     - target endpoint
     *     - comma separated list of slot ranges
     *
     * A source and a target endpoint may appear in multiple triplets.
     * There is no optimization of the source, dest, slot-range list in the SMIGRATED message.
     * The client code should read through the entire list of triplets in order to get a full
     * list of moved slots, or full list of sources and targets.
     *
     * Example:
     * [ 'SMIGRATED', 15, [ [ '127.0.0.1:6379', '127.0.0.2:6379', '123,456,789-1000' ], [ '127.0.0.3:6380', '127.0.0.4:6380', '124,457,300-500' ] ] ]
     *                ^seq     ^source1          ^destination1     ^slots                  ^source2          ^destination2    ^slots
     *
     * Result structure guarantees:
     * - Each source address appears in exactly one entry (entries are deduplicated by source)
     * - Within each entry, each destination address appears exactly once (destinations are deduplicated per source)
     * - Each destination contains the complete list of slots that moved from that source to that destination
     * - Note: The same destination address CAN appear under different sources (e.g., node X receives slots from both A and B)
     */
    static parseSMigratedPush(push) {
        const map = new Map();
        for (const [src, destination, slots] of push[2]) {
            const source = String(src);
            const [dHost, dPort] = String(destination).split(':');
            // `slots` could be mix of single slots and ranges, for example: 123,456,789-1000
            const parsedSlots = String(slots).split(',').map((singleOrRange) => {
                const separatorIndex = singleOrRange.indexOf('-');
                if (separatorIndex === -1) {
                    // Its single slot
                    return Number(singleOrRange);
                }
                // Its range
                return [Number(singleOrRange.substring(0, separatorIndex)), Number(singleOrRange.substring(separatorIndex + 1))];
            });
            const destinations = map.get(source) ?? [];
            const dest = destinations.find(d => d.addr.host === dHost && d.addr.port === Number(dPort));
            if (dest) {
                // destination already exists, just add the slots
                dest.slots = dest.slots.concat(parsedSlots);
            }
            else {
                destinations.push({
                    addr: {
                        host: dHost,
                        port: Number(dPort)
                    },
                    slots: parsedSlots
                });
            }
            map.set(source, destinations);
        }
        const entries = [];
        for (const [src, destinations] of map.entries()) {
            const [host, port] = src.split(":");
            entries.push({
                source: {
                    host,
                    port: Number(port)
                },
                destinations
            });
        }
        return {
            seqId: push[1],
            entries
        };
    }
    #getAddress() {
        (0, node_assert_1.default)(this.#options.socket !== undefined);
        (0, node_assert_1.default)("host" in this.#options.socket);
        (0, node_assert_1.default)(typeof this.#options.socket.host === "string");
        const host = this.#options.socket.host;
        (0, node_assert_1.default)(typeof this.#options.socket.port === "number");
        const port = this.#options.socket.port;
        return { host, port };
    }
}
_a = EnterpriseMaintenanceManager;
exports.default = EnterpriseMaintenanceManager;
function isPrivateIP(ip) {
    const version = (0, net_1.isIP)(ip);
    if (version === 4) {
        const octets = ip.split(".").map(Number);
        return (octets[0] === 10 ||
            (octets[0] === 172 && octets[1] >= 16 && octets[1] <= 31) ||
            (octets[0] === 192 && octets[1] === 168));
    }
    if (version === 6) {
        return (ip.startsWith("fc") || // Unique local
            ip.startsWith("fd") || // Unique local
            ip === "::1" || // Loopback
            ip.startsWith("fe80") // Link-local unicast
        );
    }
    return false;
}
async function determineEndpoint(tlsEnabled, host, options) {
    (0, node_assert_1.default)(options.maintEndpointType !== undefined);
    if (options.maintEndpointType !== "auto") {
        (0, exports.dbgMaintenance)(`Determine endpoint type: ${options.maintEndpointType}`);
        return options.maintEndpointType;
    }
    const ip = (0, net_1.isIP)(host) ? host : (await (0, promises_1.lookup)(host, { family: 0 })).address;
    const isPrivate = isPrivateIP(ip);
    let result;
    if (tlsEnabled) {
        result = isPrivate ? "internal-fqdn" : "external-fqdn";
    }
    else {
        result = isPrivate ? "internal-ip" : "external-ip";
    }
    (0, exports.dbgMaintenance)(`Determine endpoint type: ${result}`);
    return result;
}
//# sourceMappingURL=enterprise-maintenance-manager.js.map