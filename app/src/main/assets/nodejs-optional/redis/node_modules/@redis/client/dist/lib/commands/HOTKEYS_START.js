"use strict";
Object.defineProperty(exports, "__esModule", { value: true });
exports.HOTKEYS_METRICS = void 0;
/**
 * Metrics to track for hotkeys
 */
exports.HOTKEYS_METRICS = {
    CPU: 'CPU',
    NET: 'NET'
};
/**
 * HOTKEYS START command - starts hotkeys tracking
 *
 * State transitions:
 * - EMPTY -> ACTIVE
 * - STOPPED -> ACTIVE (fresh)
 * - ACTIVE -> ERROR
 */
exports.default = {
    NOT_KEYED_COMMAND: true,
    IS_READ_ONLY: false,
    /**
     * Starts hotkeys tracking with specified options.
     * @param parser - The Redis command parser
     * @param options - Configuration options for hotkeys tracking
     * @see https://redis.io/commands/hotkeys-start/
     */
    parseCommand(parser, options) {
        parser.push('HOTKEYS', 'START');
        // METRICS is required with count and at least one metric type
        parser.push('METRICS', options.METRICS.count.toString());
        if (options.METRICS.CPU) {
            parser.push('CPU');
        }
        if (options.METRICS.NET) {
            parser.push('NET');
        }
        // COUNT option
        if (options.COUNT !== undefined) {
            parser.push('COUNT', options.COUNT.toString());
        }
        // DURATION option
        if (options.DURATION !== undefined) {
            parser.push('DURATION', options.DURATION.toString());
        }
        // SAMPLE option
        if (options.SAMPLE !== undefined) {
            parser.push('SAMPLE', options.SAMPLE.toString());
        }
        // SLOTS option
        if (options.SLOTS !== undefined) {
            parser.push('SLOTS', options.SLOTS.count.toString());
            for (const slot of options.SLOTS.slots) {
                parser.push(slot.toString());
            }
        }
    },
    transformReply: undefined
};
//# sourceMappingURL=HOTKEYS_START.js.map