"use strict";
Object.defineProperty(exports, "__esModule", { value: true });
exports.default = {
    IS_READ_ONLY: false,
    /**
     * Configures the idempotency parameters for a stream's IDMP map.
     * Sets how long Redis remembers each iid and the maximum number of iids to track.
     * This command clears the existing IDMP map (Redis forgets all previously stored iids),
     * but only if the configuration value actually changes.
     *
     * @param parser - The command parser
     * @param key - The name of the stream
     * @param options - Optional idempotency configuration parameters
     * @returns 'OK' on success
     */
    parseCommand(parser, key, options) {
        parser.push('XCFGSET');
        parser.pushKey(key);
        if (options?.IDMP_DURATION !== undefined) {
            parser.push('IDMP-DURATION', options.IDMP_DURATION.toString());
        }
        if (options?.IDMP_MAXSIZE !== undefined) {
            parser.push('IDMP-MAXSIZE', options.IDMP_MAXSIZE.toString());
        }
    },
    transformReply: undefined
};
//# sourceMappingURL=XCFGSET.js.map