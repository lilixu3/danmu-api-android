Object.defineProperty(exports, Symbol.toStringTag, { value: 'Module' });
let __tracing_utils_ts = require("./tracing-utils.cjs");
let __version_ts = require("./version.cjs");

//#region src/tracing.ts
const hasTelemetry = false;
/** @internal */
const tracer = { startActiveSpan(name, fn) {
	return fn();
} };

//#endregion
exports.hasTelemetry = hasTelemetry;
exports.tracer = tracer;
//# sourceMappingURL=tracing.cjs.map