"use strict";
Object.defineProperty(exports, "__esModule", { value: true });
/**
 * HOTKEYS STOP command - stops hotkeys tracking but keeps results available for GET
 *
 * State transitions:
 * - ACTIVE -> STOPPED (returns OK)
 * - STOPPED -> STOPPED (no-op)
 * - EMPTY -> EMPTY (returns null - no session was started)
 *
 * Note: Returns null if no session was started or is already stopped.
 */
exports.default = {
    NOT_KEYED_COMMAND: true,
    IS_READ_ONLY: false,
    /**
     * Stops hotkeys tracking. Results remain available via HOTKEYS GET.
     * Returns null if no session was started or is already stopped.
     * @param parser - The Redis command parser
     * @see https://redis.io/commands/hotkeys-stop/
     */
    parseCommand(parser) {
        parser.push('HOTKEYS', 'STOP');
    },
    transformReply: undefined
};
//# sourceMappingURL=HOTKEYS_STOP.js.map