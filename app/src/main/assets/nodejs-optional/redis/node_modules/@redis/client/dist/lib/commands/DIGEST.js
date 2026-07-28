"use strict";
Object.defineProperty(exports, "__esModule", { value: true });
exports.default = {
    IS_READ_ONLY: true,
    /**
     *
     * @experimental
     *
     * Returns the XXH3 hash of a string value.
     *
     * @param parser - The Redis command parser
     * @param key - Key to get the digest of
     */
    parseCommand(parser, key) {
        parser.push("DIGEST");
        parser.pushKey(key);
    },
    transformReply: undefined,
};
//# sourceMappingURL=DIGEST.js.map