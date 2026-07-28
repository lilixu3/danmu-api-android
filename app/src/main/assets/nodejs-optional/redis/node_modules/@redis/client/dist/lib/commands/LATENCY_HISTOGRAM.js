"use strict";
Object.defineProperty(exports, "__esModule", { value: true });
const generic_transformers_1 = require("./generic-transformers");
const id = (n) => n;
exports.default = {
    CACHEABLE: false,
    IS_READ_ONLY: true,
    /**
     * Constructs the LATENCY HISTOGRAM command
     *
     * @param parser - The command parser
     * @param commands - The list of redis commands to get histogram for
     * @see https://redis.io/docs/latest/commands/latency-histogram/
     */
    parseCommand(parser, ...commands) {
        const args = ['LATENCY', 'HISTOGRAM'];
        if (commands.length !== 0) {
            args.push(...commands);
        }
        parser.push(...args);
    },
    transformReply: {
        2: (reply) => {
            const result = {};
            if (reply.length === 0)
                return result;
            for (let i = 1; i < reply.length; i += 2) {
                const histogram = reply[i];
                result[reply[i - 1]] = {
                    calls: histogram[1],
                    histogram_usec: (0, generic_transformers_1.transformTuplesToMap)(histogram[3], id),
                };
            }
            return result;
        },
        3: undefined,
    }
};
//# sourceMappingURL=LATENCY_HISTOGRAM.js.map