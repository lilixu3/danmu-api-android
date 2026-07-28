"use strict";
Object.defineProperty(exports, "__esModule", { value: true });
exports.parseMSetExArguments = exports.ExpirationMode = exports.SetMode = void 0;
const generic_transformers_1 = require("./generic-transformers");
exports.SetMode = {
    /**
     * Only set if all keys exist
     */
    XX: "XX",
    /**
     * Only set if none of the keys exist
     */
    NX: "NX",
};
exports.ExpirationMode = {
    /**
     * Relative expiration (seconds)
     */
    EX: "EX",
    /**
     * Relative expiration (milliseconds)
     */
    PX: "PX",
    /**
     * Absolute expiration (Unix timestamp in seconds)
     */
    EXAT: "EXAT",
    /**
     * Absolute expiration (Unix timestamp in milliseconds)
     */
    PXAT: "PXAT",
    /**
     * Keep existing TTL
     */
    KEEPTTL: "KEEPTTL",
};
function parseMSetExArguments(parser, keyValuePairs) {
    let tuples = [];
    if (Array.isArray(keyValuePairs)) {
        if (keyValuePairs.length == 0) {
            throw new Error("empty keyValuePairs Argument");
        }
        if (Array.isArray(keyValuePairs[0])) {
            tuples = keyValuePairs;
        }
        else {
            const arr = keyValuePairs;
            for (let i = 0; i < arr.length; i += 2) {
                tuples.push([arr[i], arr[i + 1]]);
            }
        }
    }
    else {
        for (const tuple of Object.entries(keyValuePairs)) {
            tuples.push([tuple[0], tuple[1]]);
        }
    }
    // Push the number of keys
    parser.push(tuples.length.toString());
    for (const tuple of tuples) {
        parser.pushKey(tuple[0]);
        parser.push(tuple[1]);
    }
}
exports.parseMSetExArguments = parseMSetExArguments;
exports.default = {
    /**
     * Constructs the MSETEX command.
     *
     * Atomically sets multiple string keys with a shared expiration in a single operation.
     *
     * @param parser - The command parser
     * @param keyValuePairs - Key-value pairs to set (array of tuples, flat array, or object)
     * @param options - Configuration for expiration and set modes
     * @see https://redis.io/commands/msetex/
     */
    parseCommand(parser, keyValuePairs, options) {
        parser.push("MSETEX");
        // Push number of keys and key-value pairs before the options
        parseMSetExArguments(parser, keyValuePairs);
        if (options?.mode) {
            parser.push(options.mode);
        }
        if (options?.expiration) {
            switch (options.expiration.type) {
                case exports.ExpirationMode.EXAT:
                    parser.push(exports.ExpirationMode.EXAT, (0, generic_transformers_1.transformEXAT)(options.expiration.value));
                    break;
                case exports.ExpirationMode.PXAT:
                    parser.push(exports.ExpirationMode.PXAT, (0, generic_transformers_1.transformPXAT)(options.expiration.value));
                    break;
                case exports.ExpirationMode.KEEPTTL:
                    parser.push(exports.ExpirationMode.KEEPTTL);
                    break;
                case exports.ExpirationMode.EX:
                case exports.ExpirationMode.PX:
                    parser.push(options.expiration.type, options.expiration.value?.toString());
                    break;
            }
        }
    },
    transformReply: undefined,
};
//# sourceMappingURL=MSETEX.js.map