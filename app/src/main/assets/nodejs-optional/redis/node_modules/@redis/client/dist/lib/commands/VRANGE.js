"use strict";
Object.defineProperty(exports, "__esModule", { value: true });
exports.default = {
    IS_READ_ONLY: true,
    /**
     * Returns elements in a lexicographical range from a vector set.
     * Provides a stateless iterator for elements inside a vector set.
     *
     * @param parser - The command parser
     * @param key - The key of the vector set
     * @param start - The starting point of the lexicographical range.
     *                Can be a string prefixed with `[` for inclusive (e.g., `[Redis`),
     *                `(` for exclusive (e.g., `(a7`), or `-` for the minimum element.
     * @param end - The ending point of the lexicographical range.
     *              Can be a string prefixed with `[` for inclusive,
     *              `(` for exclusive, or `+` for the maximum element.
     * @param count - Optional maximum number of elements to return.
     *                If negative, returns all elements in the specified range.
     * @see https://redis.io/commands/vrange/
     */
    parseCommand(parser, key, start, end, count) {
        parser.push('VRANGE');
        parser.pushKey(key);
        parser.push(start, end);
        if (count !== undefined) {
            parser.push(count.toString());
        }
    },
    transformReply: undefined
};
//# sourceMappingURL=VRANGE.js.map