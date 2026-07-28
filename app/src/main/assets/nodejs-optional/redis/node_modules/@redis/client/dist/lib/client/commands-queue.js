"use strict";
var __importDefault = (this && this.__importDefault) || function (mod) {
    return (mod && mod.__esModule) ? mod : { "default": mod };
};
Object.defineProperty(exports, "__esModule", { value: true });
const linked_list_1 = require("./linked-list");
const encoder_1 = __importDefault(require("../RESP/encoder"));
const decoder_1 = require("../RESP/decoder");
const pub_sub_1 = require("./pub-sub");
const errors_1 = require("../errors");
const enterprise_maintenance_manager_1 = require("./enterprise-maintenance-manager");
const PONG = Buffer.from('pong'), RESET = Buffer.from('RESET');
const RESP2_PUSH_TYPE_MAPPING = {
    ...decoder_1.PUSH_TYPE_MAPPING,
    [decoder_1.RESP_TYPES.SIMPLE_STRING]: Buffer
};
class RedisCommandsQueue {
    #respVersion;
    #maxLength;
    #toWrite = new linked_list_1.DoublyLinkedList();
    #waitingForReply = new linked_list_1.EmptyAwareSinglyLinkedList();
    #onShardedChannelMoved;
    #chainInExecution;
    decoder;
    #pubSub = new pub_sub_1.PubSub();
    #pushHandlers = [this.#onPush.bind(this)];
    #maintenanceCommandTimeout;
    setMaintenanceCommandTimeout(ms) {
        // Prevent possible api misuse
        if (this.#maintenanceCommandTimeout === ms) {
            (0, enterprise_maintenance_manager_1.dbgMaintenance)(`Queue already set maintenanceCommandTimeout to ${ms}, skipping`);
            return;
        }
        ;
        (0, enterprise_maintenance_manager_1.dbgMaintenance)(`Setting maintenance command timeout to ${ms}`);
        this.#maintenanceCommandTimeout = ms;
        if (this.#maintenanceCommandTimeout === undefined) {
            (0, enterprise_maintenance_manager_1.dbgMaintenance)(`Queue will keep maintenanceCommandTimeout for exisitng commands, just to be on the safe side. New commands will receive normal timeouts`);
            return;
        }
        let counter = 0;
        const total = this.#toWrite.length;
        // Overwrite timeouts of all eligible toWrite commands
        for (const node of this.#toWrite.nodes()) {
            const command = node.value;
            // Remove timeout listener if it exists
            RedisCommandsQueue.#removeTimeoutListener(command);
            counter++;
            const newTimeout = this.#maintenanceCommandTimeout;
            // Overwrite the command's timeout
            const signal = AbortSignal.timeout(newTimeout);
            command.timeout = {
                signal,
                listener: () => {
                    this.#toWrite.remove(node);
                    command.reject(new errors_1.CommandTimeoutDuringMaintenanceError(newTimeout));
                },
                originalTimeout: command.timeout?.originalTimeout
            };
            signal.addEventListener('abort', command.timeout.listener, { once: true });
        }
        ;
        (0, enterprise_maintenance_manager_1.dbgMaintenance)(`Total of ${counter} of ${total} timeouts reset to ${ms}`);
    }
    get isPubSubActive() {
        return this.#pubSub.isActive;
    }
    constructor(respVersion, maxLength, onShardedChannelMoved) {
        this.#respVersion = respVersion;
        this.#maxLength = maxLength;
        this.#onShardedChannelMoved = onShardedChannelMoved;
        this.decoder = this.#initiateDecoder();
    }
    #onReply(reply) {
        this.#waitingForReply.shift().resolve(reply);
    }
    #onErrorReply(err) {
        this.#waitingForReply.shift().reject(err);
    }
    #onPush(push) {
        // TODO: type
        if (this.#pubSub.handleMessageReply(push))
            return true;
        const isShardedUnsubscribe = pub_sub_1.PubSub.isShardedUnsubscribe(push);
        if (isShardedUnsubscribe && !this.#waitingForReply.length) {
            const channel = push[1].toString();
            this.#onShardedChannelMoved(channel, this.#pubSub.removeShardedListeners(channel));
            return true;
        }
        else if (isShardedUnsubscribe || pub_sub_1.PubSub.isStatusReply(push)) {
            const head = this.#waitingForReply.head.value;
            if ((Number.isNaN(head.channelsCounter) && push[2] === 0) ||
                --head.channelsCounter === 0) {
                this.#waitingForReply.shift().resolve();
            }
            return true;
        }
        return false;
    }
    #getTypeMapping() {
        return this.#waitingForReply.head.value.typeMapping ?? {};
    }
    #initiateDecoder() {
        return new decoder_1.Decoder({
            onReply: reply => this.#onReply(reply),
            onErrorReply: err => this.#onErrorReply(err),
            //TODO: we can shave off a few cycles by not adding onPush handler at all if CSC is not used
            onPush: push => {
                for (const pushHandler of this.#pushHandlers) {
                    if (pushHandler(push))
                        return;
                }
            },
            getTypeMapping: () => this.#getTypeMapping()
        });
    }
    addPushHandler(handler) {
        this.#pushHandlers.push(handler);
    }
    async waitForInflightCommandsToComplete(options) {
        // In-flight commands already completed
        if (this.#waitingForReply.length === 0) {
            return;
        }
        ;
        // Otherwise wait for in-flight commands to fire `empty` event
        return new Promise(resolve => {
            const onEmpty = () => {
                if (timeoutId)
                    clearTimeout(timeoutId);
                resolve();
            };
            let timeoutId;
            const timeoutMs = options?.timeoutMs;
            if (timeoutMs !== undefined && timeoutMs > 0) {
                timeoutId = setTimeout(() => {
                    this.#waitingForReply.events.off('empty', onEmpty);
                    const pendingCount = this.#waitingForReply.length;
                    (0, enterprise_maintenance_manager_1.dbgMaintenance)(`waitForInflightCommandsToComplete timed out after ${timeoutMs}ms with ${pendingCount} commands still waiting`);
                    if (options?.flushOnTimeout && pendingCount > 0) {
                        (0, enterprise_maintenance_manager_1.dbgMaintenance)(`Flushing ${pendingCount} commands that timed out waiting for reply`);
                        this.#flushWaitingForReply(new errors_1.TimeoutError());
                    }
                    resolve(); // Resolve instead of reject - we don't want to fail the migration
                }, timeoutMs);
            }
            this.#waitingForReply.events.once('empty', onEmpty);
        });
    }
    addCommand(args, options) {
        if (this.#maxLength && this.#toWrite.length + this.#waitingForReply.length >= this.#maxLength) {
            return Promise.reject(new Error('The queue is full'));
        }
        else if (options?.abortSignal?.aborted) {
            return Promise.reject(new errors_1.AbortError());
        }
        return new Promise((resolve, reject) => {
            let node;
            const value = {
                args,
                chainId: options?.chainId,
                abort: undefined,
                timeout: undefined,
                resolve,
                reject,
                channelsCounter: undefined,
                typeMapping: options?.typeMapping
            };
            value.slotNumber = options?.slotNumber;
            // If #maintenanceCommandTimeout was explicitly set, we should
            // use it instead of the timeout provided by the command
            const timeout = this.#maintenanceCommandTimeout ?? options?.timeout;
            const wasInMaintenance = this.#maintenanceCommandTimeout !== undefined;
            if (timeout) {
                const signal = AbortSignal.timeout(timeout);
                value.timeout = {
                    signal,
                    listener: () => {
                        this.#toWrite.remove(node);
                        value.reject(wasInMaintenance ? new errors_1.CommandTimeoutDuringMaintenanceError(timeout) : new errors_1.TimeoutError());
                    },
                    originalTimeout: options?.timeout
                };
                signal.addEventListener('abort', value.timeout.listener, { once: true });
            }
            const signal = options?.abortSignal;
            if (signal) {
                value.abort = {
                    signal,
                    listener: () => {
                        this.#toWrite.remove(node);
                        value.reject(new errors_1.AbortError());
                    }
                };
                signal.addEventListener('abort', value.abort.listener, { once: true });
            }
            node = this.#toWrite.add(value, options?.asap);
        });
    }
    #addPubSubCommand(command, asap = false, chainId) {
        return new Promise((resolve, reject) => {
            this.#toWrite.add({
                args: command.args,
                chainId,
                abort: undefined,
                timeout: undefined,
                resolve() {
                    command.resolve();
                    resolve();
                },
                reject(err) {
                    command.reject?.();
                    reject(err);
                },
                channelsCounter: command.channelsCounter,
                typeMapping: decoder_1.PUSH_TYPE_MAPPING
            }, asap);
        });
    }
    #setupPubSubHandler() {
        // RESP3 uses `onPush` to handle PubSub, so no need to modify `onReply`
        if (this.#respVersion !== 2)
            return;
        this.decoder.onReply = (reply => {
            if (Array.isArray(reply)) {
                if (this.#onPush(reply))
                    return;
                const firstElement = typeof reply[0] === 'string' ? Buffer.from(reply[0]) : reply[0];
                if (PONG.equals(firstElement)) {
                    const { resolve, typeMapping } = this.#waitingForReply.shift(), buffer = (reply[1].length === 0 ? reply[0] : reply[1]);
                    resolve(typeMapping?.[decoder_1.RESP_TYPES.SIMPLE_STRING] === Buffer ? buffer : buffer.toString());
                    return;
                }
            }
            return this.#onReply(reply);
        });
        this.decoder.getTypeMapping = () => RESP2_PUSH_TYPE_MAPPING;
    }
    subscribe(type, channels, listener, returnBuffers) {
        const command = this.#pubSub.subscribe(type, channels, listener, returnBuffers);
        if (!command)
            return;
        this.#setupPubSubHandler();
        return this.#addPubSubCommand(command);
    }
    #resetDecoderCallbacks() {
        this.decoder.onReply = (reply => this.#onReply(reply));
        this.decoder.getTypeMapping = () => this.#getTypeMapping();
    }
    unsubscribe(type, channels, listener, returnBuffers) {
        const command = this.#pubSub.unsubscribe(type, channels, listener, returnBuffers);
        if (!command)
            return;
        if (command && this.#respVersion === 2) {
            // RESP2 modifies `onReply` to handle PubSub (see #setupPubSubHandler)
            const { resolve } = command;
            command.resolve = () => {
                if (!this.#pubSub.isActive) {
                    this.#resetDecoderCallbacks();
                }
                resolve();
            };
        }
        return this.#addPubSubCommand(command);
    }
    removeAllPubSubListeners() {
        return this.#pubSub.removeAllListeners();
    }
    removeShardedPubSubListenersForSlots(slots) {
        return this.#pubSub.removeShardedPubSubListenersForSlots(slots);
    }
    resubscribe(chainId) {
        const commands = this.#pubSub.resubscribe();
        if (!commands.length)
            return;
        this.#setupPubSubHandler();
        return Promise.all(commands.map(command => this.#addPubSubCommand(command, true, chainId)));
    }
    extendPubSubChannelListeners(type, channel, listeners) {
        const command = this.#pubSub.extendChannelListeners(type, channel, listeners);
        if (!command)
            return;
        this.#setupPubSubHandler();
        return this.#addPubSubCommand(command);
    }
    extendPubSubListeners(type, listeners) {
        const command = this.#pubSub.extendTypeListeners(type, listeners);
        if (!command)
            return;
        this.#setupPubSubHandler();
        return this.#addPubSubCommand(command);
    }
    getPubSubListeners(type) {
        return this.#pubSub.listeners[type];
    }
    monitor(callback, options) {
        return new Promise((resolve, reject) => {
            const typeMapping = options?.typeMapping ?? {};
            this.#toWrite.add({
                args: ['MONITOR'],
                chainId: options?.chainId,
                abort: undefined,
                timeout: undefined,
                // using `resolve` instead of using `.then`/`await` to make sure it'll be called before processing the next reply
                resolve: () => {
                    // after running `MONITOR` only `MONITOR` and `RESET` replies are expected
                    // any other command should cause an error
                    // if `RESET` already overrides `onReply`, set monitor as it's fallback
                    if (this.#resetFallbackOnReply) {
                        this.#resetFallbackOnReply = callback;
                    }
                    else {
                        this.decoder.onReply = callback;
                    }
                    this.decoder.getTypeMapping = () => typeMapping;
                    resolve();
                },
                reject,
                channelsCounter: undefined,
                typeMapping
            }, options?.asap);
        });
    }
    resetDecoder() {
        this.#resetDecoderCallbacks();
        this.decoder.reset();
    }
    #resetFallbackOnReply;
    async reset(chainId, typeMapping) {
        return new Promise((resolve, reject) => {
            // overriding onReply to handle `RESET` while in `MONITOR` or PubSub mode
            this.#resetFallbackOnReply = this.decoder.onReply;
            this.decoder.onReply = (reply => {
                if ((typeof reply === 'string' && reply === 'RESET') ||
                    (reply instanceof Buffer && RESET.equals(reply))) {
                    this.#resetDecoderCallbacks();
                    this.#resetFallbackOnReply = undefined;
                    this.#pubSub.reset();
                    this.#waitingForReply.shift().resolve(reply);
                    return;
                }
                this.#resetFallbackOnReply(reply);
            });
            this.#toWrite.push({
                args: ['RESET'],
                chainId,
                abort: undefined,
                timeout: undefined,
                resolve,
                reject,
                channelsCounter: undefined,
                typeMapping
            });
        });
    }
    isWaitingToWrite() {
        return this.#toWrite.length > 0;
    }
    *commandsToWrite() {
        let toSend = this.#toWrite.shift();
        while (toSend) {
            let encoded;
            try {
                encoded = (0, encoder_1.default)(toSend.args);
            }
            catch (err) {
                toSend.reject(err);
                toSend = this.#toWrite.shift();
                continue;
            }
            // TODO reuse `toSend` or create new object?
            toSend.args = undefined;
            if (toSend.abort) {
                RedisCommandsQueue.#removeAbortListener(toSend);
                toSend.abort = undefined;
            }
            if (toSend.timeout) {
                RedisCommandsQueue.#removeTimeoutListener(toSend);
                toSend.timeout = undefined;
            }
            this.#chainInExecution = toSend.chainId;
            toSend.chainId = undefined;
            this.#waitingForReply.push(toSend);
            yield encoded;
            toSend = this.#toWrite.shift();
        }
    }
    #flushWaitingForReply(err) {
        for (const node of this.#waitingForReply) {
            node.reject(err);
        }
        this.#waitingForReply.reset();
    }
    static #removeAbortListener(command) {
        command.abort.signal.removeEventListener('abort', command.abort.listener);
    }
    static #removeTimeoutListener(command) {
        command.timeout?.signal.removeEventListener('abort', command.timeout.listener);
    }
    static #flushToWrite(toBeSent, err) {
        if (toBeSent.abort) {
            RedisCommandsQueue.#removeAbortListener(toBeSent);
        }
        if (toBeSent.timeout) {
            RedisCommandsQueue.#removeTimeoutListener(toBeSent);
        }
        toBeSent.reject(err);
    }
    flushWaitingForReply(err) {
        this.resetDecoder();
        this.#pubSub.reset();
        this.#flushWaitingForReply(err);
        if (!this.#chainInExecution)
            return;
        while (this.#toWrite.head?.value.chainId === this.#chainInExecution) {
            RedisCommandsQueue.#flushToWrite(this.#toWrite.shift(), err);
        }
        this.#chainInExecution = undefined;
    }
    flushAll(err) {
        this.resetDecoder();
        this.#pubSub.reset();
        this.#flushWaitingForReply(err);
        for (const node of this.#toWrite) {
            RedisCommandsQueue.#flushToWrite(node, err);
        }
        this.#toWrite.reset();
    }
    isEmpty() {
        return (this.#toWrite.length === 0 &&
            this.#waitingForReply.length === 0);
    }
    /**
     *
     * Extracts commands for the given slots from the toWrite queue.
     * Some commands dont have "slotNumber", which means they are not designated to particular slot/node.
     * We ignore those.
     */
    extractCommandsForSlots(slots) {
        const result = [];
        let current = this.#toWrite.head;
        while (current !== undefined) {
            if (current.value.slotNumber !== undefined && slots.has(current.value.slotNumber)) {
                result.push(current.value);
                const toRemove = current;
                current = current.next;
                this.#toWrite.remove(toRemove);
            }
            else {
                // Move to next node even if we don't extract this command
                current = current.next;
            }
        }
        return result;
    }
    /**
    * Gets all commands from the write queue without removing them.
    */
    extractAllCommands() {
        const result = [];
        let current = this.#toWrite.head;
        while (current) {
            result.push(current.value);
            this.#toWrite.remove(current);
            current = current.next;
        }
        return result;
    }
    /**
     * Prepends commands to the write queue in reverse.
     */
    prependCommandsToWrite(commands) {
        if (!commands.length) {
            return;
        }
        for (let i = commands.length - 1; i >= 0; i--) {
            this.#toWrite.unshift(commands[i]);
        }
    }
}
exports.default = RedisCommandsQueue;
//# sourceMappingURL=commands-queue.js.map