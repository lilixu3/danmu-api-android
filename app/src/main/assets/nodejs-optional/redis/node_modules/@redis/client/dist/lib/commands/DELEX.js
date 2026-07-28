"use strict";
Object.defineProperty(exports, "__esModule", { value: true });
exports.DelexCondition = void 0;
exports.DelexCondition = {
    /**
     * Delete if value equals match-value.
     */
    IFEQ: "IFEQ",
    /**
     * Delete if value does not equal match-value.
     */
    IFNE: "IFNE",
    /**
     * Delete if value digest equals match-digest.
     */
    IFDEQ: "IFDEQ",
    /**
     * Delete if value digest does not equal match-digest.
     */
    IFDNE: "IFDNE",
};
exports.default = {
    IS_READ_ONLY: false,
    /**
     *
     * @experimental
     *
     * Conditionally removes the specified key based on value or digest comparison.
     *
     * @param parser - The Redis command parser
     * @param key - Key to delete
     */
    parseCommand(parser, key, options) {
        parser.push("DELEX");
        parser.pushKey(key);
        if (options) {
            parser.push(options.condition);
            parser.push(options.matchValue);
        }
    },
    transformReply: undefined,
};
//# sourceMappingURL=DELEX.js.map