"use strict";
Object.defineProperty(exports, "__esModule", { value: true });
exports.LATENCY_EVENTS = void 0;
const LATENCY_GRAPH_1 = require("./LATENCY_GRAPH");
Object.defineProperty(exports, "LATENCY_EVENTS", { enumerable: true, get: function () { return LATENCY_GRAPH_1.LATENCY_EVENTS; } });
exports.default = {
    NOT_KEYED_COMMAND: true,
    IS_READ_ONLY: false,
    /**
     * Constructs the LATENCY RESET command
     * * @param parser - The command parser
     * @param events - The latency events to reset. If not specified, all events are reset.
     * @see https://redis.io/commands/latency-reset/
     */
    parseCommand(parser, ...events) {
        const args = ['LATENCY', 'RESET'];
        if (events.length > 0) {
            args.push(...events);
        }
        parser.push(...args);
    },
    transformReply: undefined
};
//# sourceMappingURL=LATENCY_RESET.js.map