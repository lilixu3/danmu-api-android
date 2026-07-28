"use strict";
var __importDefault = (this && this.__importDefault) || function (mod) {
    return (mod && mod.__esModule) ? mod : { "default": mod };
};
var _a;
Object.defineProperty(exports, "__esModule", { value: true });
exports.RESUBSCRIBE_LISTENERS_EVENT = void 0;
const errors_1 = require("../errors");
const client_1 = __importDefault(require("../client"));
const pub_sub_1 = require("../client/pub-sub");
const cluster_key_slot_1 = __importDefault(require("cluster-key-slot"));
const cache_1 = require("../client/cache");
const enterprise_maintenance_manager_1 = require("../client/enterprise-maintenance-manager");
exports.RESUBSCRIBE_LISTENERS_EVENT = '__resubscribeListeners';
class RedisClusterSlots {
    static #SLOTS = 16384;
    #options;
    #clientFactory;
    #emit;
    slots = new Array(_a.#SLOTS);
    masters = new Array();
    replicas = new Array();
    nodeByAddress = new Map();
    pubSubNode;
    clientSideCache;
    smigratedSeqIdsSeen = new Set;
    #isOpen = false;
    get isOpen() {
        return this.#isOpen;
    }
    #validateOptions(options) {
        if (options?.clientSideCache && options?.RESP !== 3) {
            throw new Error('Client Side Caching is only supported with RESP3');
        }
    }
    constructor(options, emit) {
        this.#validateOptions(options);
        this.#options = options;
        if (options?.clientSideCache) {
            if (options.clientSideCache instanceof cache_1.PooledClientSideCacheProvider) {
                this.clientSideCache = options.clientSideCache;
            }
            else {
                this.clientSideCache = new cache_1.BasicPooledClientSideCache(options.clientSideCache);
            }
        }
        this.#clientFactory = client_1.default.factory(this.#options);
        this.#emit = emit;
    }
    async connect() {
        if (this.#isOpen) {
            throw new Error('Cluster already open');
        }
        this.#isOpen = true;
        try {
            await this.#discoverWithRootNodes();
            this.#emit('connect');
        }
        catch (err) {
            this.#isOpen = false;
            throw err;
        }
    }
    async #discoverWithRootNodes() {
        let start = Math.floor(Math.random() * this.#options.rootNodes.length);
        for (let i = start; i < this.#options.rootNodes.length; i++) {
            if (!this.#isOpen)
                throw new Error('Cluster closed');
            if (await this.#discover(this.#options.rootNodes[i])) {
                return;
            }
        }
        for (let i = 0; i < start; i++) {
            if (!this.#isOpen)
                throw new Error('Cluster closed');
            if (await this.#discover(this.#options.rootNodes[i])) {
                return;
            }
        }
        throw new errors_1.RootNodesUnavailableError();
    }
    #resetSlots() {
        this.slots = new Array(_a.#SLOTS);
        this.masters = [];
        this.replicas = [];
        this._randomNodeIterator = undefined;
    }
    async #discover(rootNode) {
        this.clientSideCache?.clear();
        this.clientSideCache?.disable();
        try {
            const addressesInUse = new Set(), promises = [], eagerConnect = this.#options.minimizeConnections !== true;
            const shards = await this.#getShards(rootNode);
            this.#resetSlots(); // Reset slots AFTER shards have been fetched to prevent a race condition
            for (const { from, to, master, replicas } of shards) {
                const shard = {
                    master: this.#initiateSlotNode(master, false, eagerConnect, addressesInUse, promises)
                };
                if (this.#options.useReplicas) {
                    shard.replicas = replicas.map(replica => this.#initiateSlotNode(replica, true, eagerConnect, addressesInUse, promises));
                }
                for (let i = from; i <= to; i++) {
                    this.slots[i] = shard;
                }
            }
            if (this.pubSubNode && !addressesInUse.has(this.pubSubNode.address)) {
                const channelsListeners = this.pubSubNode.client.getPubSubListeners(pub_sub_1.PUBSUB_TYPE.CHANNELS), patternsListeners = this.pubSubNode.client.getPubSubListeners(pub_sub_1.PUBSUB_TYPE.PATTERNS);
                this.pubSubNode.client.destroy();
                if (channelsListeners.size || patternsListeners.size) {
                    promises.push(this.#initiatePubSubClient({
                        [pub_sub_1.PUBSUB_TYPE.CHANNELS]: channelsListeners,
                        [pub_sub_1.PUBSUB_TYPE.PATTERNS]: patternsListeners
                    }));
                }
            }
            //Keep only the nodes that are still in use
            for (const [address, node] of this.nodeByAddress.entries()) {
                if (addressesInUse.has(address))
                    continue;
                if (node.client) {
                    node.client.destroy();
                }
                const { pubSub } = node;
                if (pubSub) {
                    pubSub.client.destroy();
                }
                this.nodeByAddress.delete(address);
            }
            await Promise.all(promises);
            this.clientSideCache?.enable();
            return true;
        }
        catch (err) {
            this.#emit('error', err);
            return false;
        }
    }
    #handleSmigrated = async (event) => {
        (0, enterprise_maintenance_manager_1.dbgMaintenance)(`[CSlots]: handle smigrated`, JSON.stringify(event, null, 2));
        if (this.smigratedSeqIdsSeen.has(event.seqId)) {
            (0, enterprise_maintenance_manager_1.dbgMaintenance)(`[CSlots]: sequence id ${event.seqId} already seen, abort`);
            return;
        }
        this.smigratedSeqIdsSeen.add(event.seqId);
        for (const entry of event.entries) {
            const sourceAddress = `${entry.source.host}:${entry.source.port}`;
            const sourceNode = this.nodeByAddress.get(sourceAddress);
            (0, enterprise_maintenance_manager_1.dbgMaintenance)(`[CSlots]: Looking for sourceAddress=${sourceAddress}. Available addresses in nodeByAddress: ${Array.from(this.nodeByAddress.keys()).join(', ')}`);
            if (!sourceNode) {
                (0, enterprise_maintenance_manager_1.dbgMaintenance)(`[CSlots]: address ${sourceAddress} not in 'nodeByAddress', skipping this entry`);
                continue;
            }
            if (sourceNode.client === undefined) {
                (0, enterprise_maintenance_manager_1.dbgMaintenance)(`[CSlots]: Node for ${sourceAddress} does not have a client, skipping this entry`);
                continue;
            }
            // Track all slots being moved for this entry (used for pubsub listener handling)
            const allMovingSlots = new Set();
            try {
                // 1. Pausing
                // 1.1 Normal
                sourceNode.client?._pause();
                // 1.2 Sharded pubsub
                if ('pubSub' in sourceNode) {
                    sourceNode.pubSub?.client._pause();
                }
                // 2. Process each destination: create nodes, update slot mappings, extract commands, unpause
                let lastDestNode;
                for (const { addr: { host, port }, slots } of entry.destinations) {
                    const destinationAddress = `${host}:${port}`;
                    let destMasterNode = this.nodeByAddress.get(destinationAddress);
                    (0, enterprise_maintenance_manager_1.dbgMaintenance)(`[CSlots]: Looking for destAddress=${destinationAddress}. Found in nodeByAddress: ${destMasterNode ? 'YES' : 'NO'}`);
                    let destShard;
                    // 2.1 Create new Master if needed
                    if (!destMasterNode) {
                        const promises = [];
                        destMasterNode = this.#initiateSlotNode({ host: host, port: port, id: `smigrated-${host}:${port}` }, false, true, new Set(), promises);
                        await Promise.all([...promises, this.#initiateShardedPubSubClient(destMasterNode)]);
                        // Pause new destination until migration is complete
                        destMasterNode.client?._pause();
                        destMasterNode.pubSub?.client._pause();
                        // In case destination node didnt exist, this means Shard didnt exist as well, so creating a new Shard is completely fine
                        destShard = {
                            master: destMasterNode
                        };
                    }
                    else {
                        // DEBUG: Log all master hosts/ports in slots array to diagnose mismatch
                        const allMasters = [...new Set(this.slots)].map(s => `${s.master.host}:${s.master.port}`);
                        (0, enterprise_maintenance_manager_1.dbgMaintenance)(`[CSlots]: Searching for shard with host=${host}, port=${port}. Available masters in slots: ${allMasters.join(', ')}`);
                        // In case destination node existed, this means there was a Shard already, so its best if we can find it.
                        const existingShard = this.slots.find(shard => shard.master.host === host && shard.master.port === port);
                        if (!existingShard) {
                            (0, enterprise_maintenance_manager_1.dbgMaintenance)("Could not find shard");
                            throw new Error('Could not find shard');
                        }
                        destShard = existingShard;
                        // Pause existing destination during command transfer
                        destMasterNode.client?._pause();
                        destMasterNode.pubSub?.client._pause();
                    }
                    // Track last destination for slotless commands later
                    lastDestNode = destMasterNode;
                    // 3. Convert slots to Set and update shard mappings
                    const destinationSlots = new Set();
                    for (const slot of slots) {
                        if (typeof slot === 'number') {
                            this.slots[slot] = destShard;
                            destinationSlots.add(slot);
                            allMovingSlots.add(slot);
                        }
                        else {
                            for (let s = slot[0]; s <= slot[1]; s++) {
                                this.slots[s] = destShard;
                                destinationSlots.add(s);
                                allMovingSlots.add(s);
                            }
                        }
                    }
                    (0, enterprise_maintenance_manager_1.dbgMaintenance)(`[CSlots]: Updated slots to point to destination ${destMasterNode.address}. Sample slots: ${Array.from(slots).slice(0, 5).join(', ')}${slots.length > 5 ? '...' : ''}`);
                    // 4. Extract commands for this destination's slots and prepend to destination queue
                    const commandsForDestination = sourceNode.client._getQueue().extractCommandsForSlots(destinationSlots);
                    destMasterNode.client?._getQueue().prependCommandsToWrite(commandsForDestination);
                    (0, enterprise_maintenance_manager_1.dbgMaintenance)(`[CSlots]: Extracted ${commandsForDestination.length} commands for ${destinationSlots.size} slots, prepended to ${destMasterNode.address}`);
                    // 5. Unpause destination
                    destMasterNode.client?._unpause();
                    destMasterNode.pubSub?.client._unpause();
                }
                (0, enterprise_maintenance_manager_1.dbgMaintenance)(`[CSlots]: Total ${allMovingSlots.size} slots moved from ${sourceAddress}. Sample: ${Array.from(allMovingSlots).slice(0, 10).join(', ')}${allMovingSlots.size > 10 ? '...' : ''}`);
                // 6. Wait for inflight commands on source to complete (with timeout to prevent hangs)
                const INFLIGHT_TIMEOUT_MS = 5000; // 5 seconds max wait for inflight commands
                const inflightPromises = [];
                const inflightOptions = { timeoutMs: INFLIGHT_TIMEOUT_MS, flushOnTimeout: true };
                inflightPromises.push(sourceNode.client._getQueue().waitForInflightCommandsToComplete(inflightOptions));
                if ('pubSub' in sourceNode && sourceNode.pubSub !== undefined) {
                    inflightPromises.push(sourceNode.pubSub.client._getQueue().waitForInflightCommandsToComplete(inflightOptions));
                }
                if (this.pubSubNode?.address === sourceAddress) {
                    inflightPromises.push(this.pubSubNode.client._getQueue().waitForInflightCommandsToComplete(inflightOptions));
                }
                await Promise.all(inflightPromises);
                // 7. Handle source cleanup
                const sourceStillHasSlots = this.slots.find(slot => slot.master.address === sourceAddress) !== undefined;
                if (sourceStillHasSlots) {
                    // Handle sharded pubsub listeners for moving slots
                    if ('pubSub' in sourceNode) {
                        const listeners = sourceNode.pubSub?.client._getQueue().removeShardedPubSubListenersForSlots(allMovingSlots);
                        this.#emit(exports.RESUBSCRIBE_LISTENERS_EVENT, listeners);
                    }
                    // Unpause source since it still has slots
                    sourceNode.client?._unpause();
                    if ('pubSub' in sourceNode) {
                        sourceNode.pubSub?.client._unpause();
                    }
                }
                else {
                    // Source has no slots left - move remaining slotless commands and cleanup
                    const remainingCommands = sourceNode.client._getQueue().extractAllCommands();
                    if (remainingCommands.length > 0 && lastDestNode) {
                        lastDestNode.client?._getQueue().prependCommandsToWrite(remainingCommands);
                        // Trigger write scheduling since commands were added after destination was unpaused
                        lastDestNode.client?._unpause();
                        (0, enterprise_maintenance_manager_1.dbgMaintenance)(`[CSlots]: Moved ${remainingCommands.length} remaining slotless commands to ${lastDestNode.address}`);
                    }
                    if ('pubSub' in sourceNode) {
                        const listeners = sourceNode.pubSub?.client._getQueue().removeAllPubSubListeners();
                        this.#emit(exports.RESUBSCRIBE_LISTENERS_EVENT, listeners);
                    }
                    // Remove all local references to the dying shard's clients
                    this.masters = this.masters.filter(master => master.address !== sourceAddress);
                    this.replicas = this.replicas.filter(replica => replica.address !== sourceAddress);
                    this.nodeByAddress.delete(sourceAddress);
                    // Handle pubSubNode replacement BEFORE destroying source connections
                    // This ensures subscriptions are resubscribed on a new node before the old connection is lost
                    if (this.pubSubNode?.address === sourceAddress) {
                        const channelsListeners = this.pubSubNode.client.getPubSubListeners(pub_sub_1.PUBSUB_TYPE.CHANNELS), patternsListeners = this.pubSubNode.client.getPubSubListeners(pub_sub_1.PUBSUB_TYPE.PATTERNS);
                        const oldPubSubClient = this.pubSubNode.client;
                        if (channelsListeners.size || patternsListeners.size) {
                            await this.#initiatePubSubClient({
                                [pub_sub_1.PUBSUB_TYPE.CHANNELS]: channelsListeners,
                                [pub_sub_1.PUBSUB_TYPE.PATTERNS]: patternsListeners
                            });
                        }
                        else {
                            this.pubSubNode = undefined;
                        }
                        oldPubSubClient.destroy();
                    }
                    // Destroy source connections (use destroy() instead of close() since the node is being removed
                    // and close() can hang if the server is not responding)
                    sourceNode.client?.destroy();
                    if ('pubSub' in sourceNode) {
                        sourceNode.pubSub?.client.destroy();
                    }
                }
            }
            catch (err) {
                (0, enterprise_maintenance_manager_1.dbgMaintenance)(`[CSlots]: Error during SMIGRATED handling for source ${sourceAddress}: ${err}`);
                // Ensure we unpause source on error to prevent deadlock
                sourceNode.client?._unpause();
                if ('pubSub' in sourceNode) {
                    sourceNode.pubSub?.client._unpause();
                }
                this.#emit('error', err);
            }
        }
    };
    async #getShards(rootNode) {
        const options = this.#clientOptionsDefaults(rootNode);
        options.socket ??= {};
        options.socket.reconnectStrategy = false;
        options.RESP = this.#options.RESP;
        options.commandOptions = undefined;
        options.maintNotifications = 'disabled';
        // TODO: find a way to avoid type casting
        const client = await this.#clientFactory(options)
            .on('error', err => this.#emit('error', err))
            .connect();
        try {
            // switch to `CLUSTER SHARDS` when Redis 7.0 will be the minimum supported version
            return await client.clusterSlots();
        }
        finally {
            client.destroy();
        }
    }
    #getNodeAddress(address) {
        switch (typeof this.#options.nodeAddressMap) {
            case 'object':
                return this.#options.nodeAddressMap[address];
            case 'function':
                return this.#options.nodeAddressMap(address);
        }
    }
    #clientOptionsDefaults(options) {
        if (!this.#options.defaults)
            return options;
        let socket;
        if (this.#options.defaults.socket) {
            socket = {
                ...this.#options.defaults.socket,
                ...options?.socket
            };
        }
        else {
            socket = options?.socket;
        }
        return {
            ...this.#options.defaults,
            ...options,
            socket: socket
        };
    }
    #initiateSlotNode(shard, readonly, eagerConnent, addressesInUse, promises) {
        const address = `${shard.host}:${shard.port}`;
        let node = this.nodeByAddress.get(address);
        if (!node) {
            node = {
                ...shard,
                address,
                readonly,
                client: undefined,
                connectPromise: undefined
            };
            if (eagerConnent) {
                promises.push(this.#createNodeClient(node));
            }
            this.nodeByAddress.set(address, node);
        }
        if (!addressesInUse.has(address)) {
            addressesInUse.add(address);
            (readonly ? this.replicas : this.masters).push(node);
        }
        return node;
    }
    #createClient(node, readonly = node.readonly) {
        const socket = this.#getNodeAddress(node.address) ??
            { host: node.host, port: node.port, };
        const clientInfo = Object.freeze({
            host: socket.host,
            port: socket.port,
        });
        const emit = this.#emit;
        const client = this.#clientFactory(this.#clientOptionsDefaults({
            clientSideCache: this.clientSideCache,
            RESP: this.#options.RESP,
            socket,
            readonly,
        }))
            .on('error', error => emit('node-error', error, clientInfo))
            .on('reconnecting', () => emit('node-reconnecting', clientInfo))
            .once('ready', () => emit('node-ready', clientInfo))
            .once('connect', () => emit('node-connect', clientInfo))
            .once('end', () => emit('node-disconnect', clientInfo))
            .on(enterprise_maintenance_manager_1.SMIGRATED_EVENT, this.#handleSmigrated)
            .on('__MOVED', async (allPubSubListeners) => {
            await this.rediscover(client);
            this.#emit(exports.RESUBSCRIBE_LISTENERS_EVENT, allPubSubListeners);
        });
        return client;
    }
    #createNodeClient(node, readonly) {
        const client = node.client = this.#createClient(node, readonly);
        return node.connectPromise = client.connect()
            .finally(() => node.connectPromise = undefined);
    }
    nodeClient(node) {
        // if the node is connecting
        if (node.connectPromise)
            return node.connectPromise;
        // if the node is connected
        if (node.client)
            return Promise.resolve(node.client);
        // if the not is disconnected
        return this.#createNodeClient(node);
    }
    #runningRediscoverPromise;
    async rediscover(startWith) {
        this.#runningRediscoverPromise ??= this.#rediscover(startWith)
            .finally(() => {
            this.#runningRediscoverPromise = undefined;
        });
        return this.#runningRediscoverPromise;
    }
    async #rediscover(startWith) {
        if (await this.#discover(startWith.options))
            return;
        return this.#discoverWithRootNodes();
    }
    /**
     * @deprecated Use `close` instead.
     */
    quit() {
        return this.#destroy(client => client.quit());
    }
    /**
     * @deprecated Use `destroy` instead.
     */
    disconnect() {
        return this.#destroy(client => client.disconnect());
    }
    close() {
        return this.#destroy(client => client.close());
    }
    destroy() {
        this.#isOpen = false;
        for (const client of this.#clients()) {
            client.destroy();
        }
        if (this.pubSubNode) {
            this.pubSubNode.client.destroy();
            this.pubSubNode = undefined;
        }
        this.#resetSlots();
        this.nodeByAddress.clear();
        this.#emit('disconnect');
    }
    *#clients() {
        for (const master of this.masters) {
            if (master.client) {
                yield master.client;
            }
            if (master.pubSub) {
                yield master.pubSub.client;
            }
        }
        for (const replica of this.replicas) {
            if (replica.client) {
                yield replica.client;
            }
        }
    }
    async #destroy(fn) {
        this.#isOpen = false;
        const promises = [];
        for (const client of this.#clients()) {
            promises.push(fn(client));
        }
        if (this.pubSubNode) {
            promises.push(fn(this.pubSubNode.client));
            this.pubSubNode = undefined;
        }
        this.#resetSlots();
        this.nodeByAddress.clear();
        await Promise.allSettled(promises);
        this.#emit('disconnect');
    }
    async getClientAndSlotNumber(firstKey, isReadonly) {
        if (!firstKey) {
            return {
                client: await this.nodeClient(this.getRandomNode())
            };
        }
        const slotNumber = (0, cluster_key_slot_1.default)(firstKey);
        if (!isReadonly) {
            return {
                client: await this.nodeClient(this.slots[slotNumber].master),
                slotNumber
            };
        }
        return {
            client: await this.nodeClient(this.getSlotRandomNode(slotNumber)),
            slotNumber
        };
    }
    *#iterateAllNodes() {
        if (this.masters.length + this.replicas.length === 0)
            return;
        let i = Math.floor(Math.random() * (this.masters.length + this.replicas.length));
        if (i < this.masters.length) {
            do {
                yield this.masters[i];
            } while (++i < this.masters.length);
            for (const replica of this.replicas) {
                yield replica;
            }
        }
        else {
            i -= this.masters.length;
            do {
                yield this.replicas[i];
            } while (++i < this.replicas.length);
        }
        while (true) {
            for (const master of this.masters) {
                yield master;
            }
            for (const replica of this.replicas) {
                yield replica;
            }
        }
    }
    _randomNodeIterator;
    getRandomNode() {
        this._randomNodeIterator ??= this.#iterateAllNodes();
        return this._randomNodeIterator.next().value;
    }
    *#slotNodesIterator(slot) {
        let i = Math.floor(Math.random() * (1 + slot.replicas.length));
        if (i < slot.replicas.length) {
            do {
                yield slot.replicas[i];
            } while (++i < slot.replicas.length);
        }
        while (true) {
            yield slot.master;
            for (const replica of slot.replicas) {
                yield replica;
            }
        }
    }
    getSlotRandomNode(slotNumber) {
        const slot = this.slots[slotNumber];
        if (!slot.replicas?.length) {
            return slot.master;
        }
        slot.nodesIterator ??= this.#slotNodesIterator(slot);
        return slot.nodesIterator.next().value;
    }
    getMasterByAddress(address) {
        const master = this.nodeByAddress.get(address);
        if (!master)
            return;
        return this.nodeClient(master);
    }
    getPubSubClient() {
        if (!this.pubSubNode)
            return this.#initiatePubSubClient();
        return this.pubSubNode.connectPromise ?? Promise.resolve(this.pubSubNode.client);
    }
    async #initiatePubSubClient(toResubscribe) {
        const index = Math.floor(Math.random() * (this.masters.length + this.replicas.length)), node = index < this.masters.length ?
            this.masters[index] :
            this.replicas[index - this.masters.length], client = this.#createClient(node, false);
        this.pubSubNode = {
            address: node.address,
            client,
            connectPromise: client.connect()
                .then(async (client) => {
                if (toResubscribe) {
                    await Promise.all([
                        client.extendPubSubListeners(pub_sub_1.PUBSUB_TYPE.CHANNELS, toResubscribe[pub_sub_1.PUBSUB_TYPE.CHANNELS]),
                        client.extendPubSubListeners(pub_sub_1.PUBSUB_TYPE.PATTERNS, toResubscribe[pub_sub_1.PUBSUB_TYPE.PATTERNS])
                    ]);
                }
                this.pubSubNode.connectPromise = undefined;
                return client;
            })
                .catch(err => {
                this.pubSubNode = undefined;
                throw err;
            })
        };
        return this.pubSubNode.connectPromise;
    }
    async executeUnsubscribeCommand(unsubscribe) {
        const client = await this.getPubSubClient();
        await unsubscribe(client);
        if (!client.isPubSubActive) {
            client.destroy();
            this.pubSubNode = undefined;
        }
    }
    getShardedPubSubClient(channel) {
        const { master } = this.slots[(0, cluster_key_slot_1.default)(channel)];
        if (!master.pubSub)
            return this.#initiateShardedPubSubClient(master);
        return master.pubSub.connectPromise ?? Promise.resolve(master.pubSub.client);
    }
    async #initiateShardedPubSubClient(master) {
        const client = this.#createClient(master, false)
            .on('server-sunsubscribe', async (channel, listeners) => {
            try {
                await this.rediscover(client);
                const redirectTo = await this.getShardedPubSubClient(channel);
                await redirectTo.extendPubSubChannelListeners(pub_sub_1.PUBSUB_TYPE.SHARDED, channel, listeners);
            }
            catch (err) {
                this.#emit('sharded-shannel-moved-error', err, channel, listeners);
            }
        });
        master.pubSub = {
            client,
            connectPromise: client.connect()
                .then(client => {
                master.pubSub.connectPromise = undefined;
                return client;
            })
                .catch(err => {
                master.pubSub = undefined;
                throw err;
            })
        };
        return master.pubSub.connectPromise;
    }
    async executeShardedUnsubscribeCommand(channel, unsubscribe) {
        const { master } = this.slots[(0, cluster_key_slot_1.default)(channel)];
        if (!master.pubSub)
            return;
        const client = master.pubSub.connectPromise ?
            await master.pubSub.connectPromise :
            master.pubSub.client;
        await unsubscribe(client);
        if (!client.isPubSubActive) {
            client.destroy();
            master.pubSub = undefined;
        }
    }
}
_a = RedisClusterSlots;
exports.default = RedisClusterSlots;
//# sourceMappingURL=cluster-slots.js.map