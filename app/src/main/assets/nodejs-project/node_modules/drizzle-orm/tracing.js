import { iife } from "./tracing-utils.js";
import { npmVersion } from "./version.js";

//#region src/tracing.ts
const hasTelemetry = false;
/** @internal */
const tracer = { startActiveSpan(name, fn) {
	return fn();
} };

//#endregion
export { hasTelemetry, tracer };
//# sourceMappingURL=tracing.js.map