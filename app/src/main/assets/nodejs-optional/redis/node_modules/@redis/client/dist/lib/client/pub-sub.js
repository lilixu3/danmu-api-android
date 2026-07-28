"use strict";
var __importDefault = (this && this.__importDefault) || function (mod) {
    return (mod && mod.__esModule) ? mod : { "default": mod };
};
Object.defineProperty(exports, "__esModule", { value: true });
exports.PubSub = exports.PUBSUB_TYPE = void 0;
const cluster_key_slot_1 = __importDefault(require("cluster-key-slot"));
exports.PUBSUB_TYPE = {
    CHANNELS: 'CHANNELS',
    PATTERNS: 'PATTERNS',
    SHARDED: 'SHARDED'
};
const COMMANDS = {
    [exports.PUBSUB_TYPE.CHANNELS]: {
        subscribe: Buffer.from('subscribe'),
        unsubscribe: Buffer.from('unsubscribe'),
        message: Buffer.from('message')
    },
    [exports.PUBSUB_TYPE.PATTERNS]: {
        subscribe: Buffer.from('psubscribe'),
        unsubscribe: Buffer.from('punsubscribe'),
        message: Buffer.from('pmessage')
    },
    [exports.PUBSUB_TYPE.SHARDED]: {
        subscribe: Buffer.from('ssubscribe'),
        unsubscribe: Buffer.from('sunsubscribe'),
        message: Buffer.from('smessage')
    }
};
class PubSub {
    static isStatusReply(reply) {
        const firstElement = typeof reply[0] === 'string' ? Buffer.from(reply[0]) : reply[0];
        return (COMMANDS[exports.PUBSUB_TYPE.CHANNELS].subscribe.equals(firstElement) ||
            COMMANDS[exports.PUBSUB_TYPE.CHANNELS].unsubscribe.equals(firstElement) ||
            COMMANDS[exports.PUBSUB_TYPE.PATTERNS].subscribe.equals(firstElement) ||
            COMMANDS[exports.PUBSUB_TYPE.PATTERNS].unsubscribe.equals(firstElement) ||
            COMMANDS[exports.PUBSUB_TYPE.SHARDED].subscribe.equals(firstElement));
    }
    static isShardedUnsubscribe(reply) {
        const firstElement = typeof reply[0] === 'string' ? Buffer.from(reply[0]) : reply[0];
        return COMMANDS[exports.PUBSUB_TYPE.SHARDED].unsubscribe.equals(firstElement);
    }
    static #channelsArray(channels) {
        return (Array.isArray(channels) ? channels : [channels]);
    }
    static #listenersSet(listeners, returnBuffers) {
        return (returnBuffers ? listeners.buffers : listeners.strings);
    }
    #subscribing = 0;
    #isActive = false;
    get isActive() {
        return this.#isActive;
    }
    listeners = {
        [exports.PUBSUB_TYPE.CHANNELS]: new Map(),
        [exports.PUBSUB_TYPE.PATTERNS]: new Map(),
        [exports.PUBSUB_TYPE.SHARDED]: new Map()
    };
    subscribe(type, channels, listener, returnBuffers) {
        const args = [COMMANDS[type].subscribe], channelsArray = PubSub.#channelsArray(channels);
        for (const channel of channelsArray) {
            let channelListeners = this.listeners[type].get(channel);
            if (!channelListeners || channelListeners.unsubscribing) {
                args.push(channel);
            }
        }
        if (args.length === 1) {
            // all channels are already subscribed, add listeners without issuing a command
            for (const channel of channelsArray) {
                PubSub.#listenersSet(this.listeners[type].get(channel), returnBuffers).add(listener);
            }
            return;
        }
        this.#isActive = true;
        this.#subscribing++;
        return {
            args,
            channelsCounter: args.length - 1,
            resolve: () => {
                this.#subscribing--;
                for (const channel of channelsArray) {
                    let listeners = this.listeners[type].get(channel);
                    if (!listeners) {
                        listeners = {
                            unsubscribing: false,
                            buffers: new Set(),
                            strings: new Set()
                        };
                        this.listeners[type].set(channel, listeners);
                    }
                    PubSub.#listenersSet(listeners, returnBuffers).add(listener);
                }
            },
            reject: () => {
                this.#subscribing--;
                this.#updateIsActive();
            }
        };
    }
    extendChannelListeners(type, channel, listeners) {
        if (!this.#extendChannelListeners(type, channel, listeners))
            return;
        this.#isActive = true;
        this.#subscribing++;
        return {
            args: [
                COMMANDS[type].subscribe,
                channel
            ],
            channelsCounter: 1,
            resolve: () => this.#subscribing--,
            reject: () => {
                this.#subscribing--;
                this.#updateIsActive();
            }
        };
    }
    #extendChannelListeners(type, channel, listeners) {
        const existingListeners = this.listeners[type].get(channel);
        if (!existingListeners) {
            this.listeners[type].set(channel, listeners);
            return true;
        }
        for (const listener of listeners.buffers) {
            existingListeners.buffers.add(listener);
        }
        for (const listener of listeners.strings) {
            existingListeners.strings.add(listener);
        }
        return false;
    }
    extendTypeListeners(type, listeners) {
        const args = [COMMANDS[type].subscribe];
        for (const [channel, channelListeners] of listeners) {
            if (this.#extendChannelListeners(type, channel, channelListeners)) {
                args.push(channel);
            }
        }
        if (args.length === 1)
            return;
        this.#isActive = true;
        this.#subscribing++;
        return {
            args,
            channelsCounter: args.length - 1,
            resolve: () => this.#subscribing--,
            reject: () => {
                this.#subscribing--;
                this.#updateIsActive();
            }
        };
    }
    unsubscribe(type, channels, listener, returnBuffers) {
        const listeners = this.listeners[type];
        if (!channels) {
            return this.#unsubscribeCommand([COMMANDS[type].unsubscribe], 
            // cannot use `this.#subscribed` because there might be some `SUBSCRIBE` commands in the queue
            // cannot use `this.#subscribed + this.#subscribing` because some `SUBSCRIBE` commands might fail
            NaN, () => listeners.clear());
        }
        const channelsArray = PubSub.#channelsArray(channels);
        if (!listener) {
            return this.#unsubscribeCommand([COMMANDS[type].unsubscribe, ...channelsArray], channelsArray.length, () => {
                for (const channel of channelsArray) {
                    listeners.delete(channel);
                }
            });
        }
        const args = [COMMANDS[type].unsubscribe];
        for (const channel of channelsArray) {
            const sets = listeners.get(channel);
            if (sets) {
                let current, other;
                if (returnBuffers) {
                    current = sets.buffers;
                    other = sets.strings;
                }
                else {
                    current = sets.strings;
                    other = sets.buffers;
                }
                const currentSize = current.has(listener) ? current.size - 1 : current.size;
                if (currentSize !== 0 || other.size !== 0)
                    continue;
                sets.unsubscribing = true;
            }
            args.push(channel);
        }
        if (args.length === 1) {
            // all channels has other listeners,
            // delete the listeners without issuing a command
            for (const channel of channelsArray) {
                PubSub.#listenersSet(listeners.get(channel), returnBuffers).delete(listener);
            }
            return;
        }
        return this.#unsubscribeCommand(args, args.length - 1, () => {
            for (const channel of channelsArray) {
                const sets = listeners.get(channel);
                if (!sets)
                    continue;
                (returnBuffers ? sets.buffers : sets.strings).delete(listener);
                if (sets.buffers.size === 0 && sets.strings.size === 0) {
                    listeners.delete(channel);
                }
            }
        });
    }
    #unsubscribeCommand(args, channelsCounter, removeListeners) {
        return {
            args,
            channelsCounter,
            resolve: () => {
                removeListeners();
                this.#updateIsActive();
            },
            reject: undefined
        };
    }
    #updateIsActive() {
        this.#isActive = (this.listeners[exports.PUBSUB_TYPE.CHANNELS].size !== 0 ||
            this.listeners[exports.PUBSUB_TYPE.PATTERNS].size !== 0 ||
            this.listeners[exports.PUBSUB_TYPE.SHARDED].size !== 0 ||
            this.#subscribing !== 0);
    }
    reset() {
        this.#isActive = false;
        this.#subscribing = 0;
    }
    resubscribe() {
        const commands = [];
        for (const [type, listeners] of Object.entries(this.listeners)) {
            if (!listeners.size)
                continue;
            this.#isActive = true;
            if (type === exports.PUBSUB_TYPE.SHARDED) {
                this.#shardedResubscribe(commands, listeners);
            }
            else {
                this.#normalResubscribe(commands, type, listeners);
            }
        }
        return commands;
    }
    #normalResubscribe(commands, type, listeners) {
        this.#subscribing++;
        const callback = () => this.#subscribing--;
        commands.push({
            args: [
                COMMANDS[type].subscribe,
                ...listeners.keys()
            ],
            channelsCounter: listeners.size,
            resolve: callback,
            reject: callback
        });
    }
    #shardedResubscribe(commands, listeners) {
        const callback = () => this.#subscribing--;
        for (const channel of listeners.keys()) {
            this.#subscribing++;
            commands.push({
                args: [
                    COMMANDS[exports.PUBSUB_TYPE.SHARDED].subscribe,
                    channel
                ],
                channelsCounter: 1,
                resolve: callback,
                reject: callback
            });
        }
    }
    handleMessageReply(reply) {
        const firstElement = typeof reply[0] === 'string' ? Buffer.from(reply[0]) : reply[0];
        if (COMMANDS[exports.PUBSUB_TYPE.CHANNELS].message.equals(firstElement)) {
            this.#emitPubSubMessage(exports.PUBSUB_TYPE.CHANNELS, reply[2], reply[1]);
            return true;
        }
        else if (COMMANDS[exports.PUBSUB_TYPE.PATTERNS].message.equals(firstElement)) {
            this.#emitPubSubMessage(exports.PUBSUB_TYPE.PATTERNS, reply[3], reply[2], reply[1]);
            return true;
        }
        else if (COMMANDS[exports.PUBSUB_TYPE.SHARDED].message.equals(firstElement)) {
            this.#emitPubSubMessage(exports.PUBSUB_TYPE.SHARDED, reply[2], reply[1]);
            return true;
        }
        return false;
    }
    removeShardedListeners(channel) {
        const listeners = this.listeners[exports.PUBSUB_TYPE.SHARDED].get(channel);
        this.listeners[exports.PUBSUB_TYPE.SHARDED].delete(channel);
        this.#updateIsActive();
        return listeners;
    }
    removeAllListeners() {
        const result = {
            [exports.PUBSUB_TYPE.CHANNELS]: this.listeners[exports.PUBSUB_TYPE.CHANNELS],
            [exports.PUBSUB_TYPE.PATTERNS]: this.listeners[exports.PUBSUB_TYPE.PATTERNS],
            [exports.PUBSUB_TYPE.SHARDED]: this.listeners[exports.PUBSUB_TYPE.SHARDED]
        };
        this.#updateIsActive();
        this.listeners[exports.PUBSUB_TYPE.CHANNELS] = new Map();
        this.listeners[exports.PUBSUB_TYPE.PATTERNS] = new Map();
        this.listeners[exports.PUBSUB_TYPE.SHARDED] = new Map();
        return result;
    }
    removeShardedPubSubListenersForSlots(slots) {
        const sharded = new Map();
        for (const [chanel, value] of this.listeners[exports.PUBSUB_TYPE.SHARDED]) {
            if (slots.has((0, cluster_key_slot_1.default)(chanel))) {
                sharded.set(chanel, value);
                this.listeners[exports.PUBSUB_TYPE.SHARDED].delete(chanel);
            }
        }
        this.#updateIsActive();
        return {
            [exports.PUBSUB_TYPE.SHARDED]: sharded
        };
    }
    #emitPubSubMessage(type, message, channel, pattern) {
        const keyString = (pattern ?? channel).toString(), listeners = this.listeners[type].get(keyString);
        if (!listeners)
            return;
        for (const listener of listeners.buffers) {
            listener(message, channel);
        }
        if (!listeners.strings.size)
            return;
        const channelString = pattern ? channel.toString() : keyString, messageString = channelString === '__redis__:invalidate' ?
            // https://github.com/redis/redis/pull/7469
            // https://github.com/redis/redis/issues/7463
            (message === null ? null : message.map(x => x.toString())) :
            message.toString();
        for (const listener of listeners.strings) {
            listener(messageString, channelString);
        }
    }
}
exports.PubSub = PubSub;
//# sourceMappingURL=pub-sub.js.map