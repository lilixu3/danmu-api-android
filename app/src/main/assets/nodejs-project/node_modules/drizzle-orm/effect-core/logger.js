import { entityKind } from "../entity.js";
import * as Effect from "effect/Effect";
import * as Context from "effect/Context";
import * as Layer from "effect/Layer";

//#region src/effect-core/logger.ts
/**
* Effect service for logging SQL queries in Drizzle ORM.
*
* By default, this service is a no-op (no logging occurs). Use `EffectLogger.layer`
* to enable Effect-based logging, or `EffectLogger.fromDrizzle` to adapt a standard
* Drizzle logger.
*
* @example
* ```ts
* // Use default (no logging)
* const db = yield* PgDrizzle.make({ relations }).pipe(
*   Effect.provide(PgDrizzle.DefaultServices),
* );
*
* // Enable Effect-based logging
* const db = yield* PgDrizzle.make({ relations }).pipe(
*   Effect.provide(EffectLogger.layer),
*   Effect.provide(PgDrizzle.DefaultServices),
* );
*
* // Use a custom Drizzle logger
* const db = yield* PgDrizzle.make({ relations }).pipe(
*   Effect.provide(EffectLogger.layerFromDrizzle(myLogger)),
*   Effect.provide(PgDrizzle.DefaultServices),
* );
* ```
*/
var EffectLogger = class EffectLogger extends Context.Service()("drizzle-orm/EffectLogger", { make: Effect.sync(() => ({ logQuery: (_query, _params) => Effect.void })) }) {
	static [entityKind] = "drizzle-orm/EffectLogger";
	/**
	* The default layer providing a no-op logger.
	*/
	static Default = Layer.effect(EffectLogger, EffectLogger.make);
	/**
	* Creates an EffectLoggerShape from a standard Drizzle logger.
	*
	* @param logger - A Drizzle logger instance implementing the `Logger` interface.
	* @returns A new EffectLoggerShape that delegates to the provided Drizzle logger.
	*
	* @example
	* ```ts
	* const drizzleLogger = new DefaultLogger();
	* const effectLogger = EffectLogger.fromDrizzle(drizzleLogger);
	* ```
	*/
	static fromDrizzle(logger) {
		return { logQuery: (query, params) => {
			return Effect.sync(() => logger.logQuery(query, params));
		} };
	}
	/**
	* Creates a Layer that provides an EffectLogger from a standard Drizzle logger.
	*
	* @param logger - A Drizzle logger instance implementing the `Logger` interface.
	* @returns A Layer that provides the EffectLogger service.
	*
	* @example
	* ```ts
	* const drizzleLogger = new DefaultLogger();
	* const db = yield* PgDrizzle.make({ relations }).pipe(
	*   Effect.provide(EffectLogger.layerFromDrizzle(drizzleLogger)),
	*   Effect.provide(PgDrizzle.DefaultServices),
	* );
	* ```
	*/
	static layerFromDrizzle(logger) {
		return Layer.succeed(EffectLogger, EffectLogger.fromDrizzle(logger));
	}
	/**
	* A Layer that provides an EffectLogger with Effect-based logging.
	*
	* This layer logs queries using `Effect.log()` with annotations for the query
	* SQL and parameters. Use this when you want query logging integrated with
	* Effect's logging infrastructure.
	*
	* @example
	* ```ts
	* const db = yield* PgDrizzle.make({ relations }).pipe(
	*   Effect.provide(EffectLogger.layer),
	*   Effect.provide(PgDrizzle.DefaultServices),
	* );
	* ```
	*/
	static layer = Layer.succeed(EffectLogger, { logQuery: Effect.fn("EffectLogger.logQuery")(function* (query, params) {
		const stringifiedParams = params.map((p) => {
			try {
				return JSON.stringify(p);
			} catch {
				return String(p);
			}
		});
		yield* Effect.log().pipe(Effect.annotateLogs({
			query,
			params: stringifiedParams
		}));
	}) });
};

//#endregion
export { EffectLogger };
//# sourceMappingURL=logger.js.map