Object.defineProperty(exports, Symbol.toStringTag, { value: 'Module' });
const require_runtime = require('../_virtual/_rolldown/runtime.cjs');
let __entity_ts = require("../entity.cjs");
let effect_Effect = require("effect/Effect");
effect_Effect = require_runtime.__toESM(effect_Effect);
let effect_Context = require("effect/Context");
effect_Context = require_runtime.__toESM(effect_Context);
let effect_Layer = require("effect/Layer");
effect_Layer = require_runtime.__toESM(effect_Layer);

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
var EffectLogger = class EffectLogger extends effect_Context.Service()("drizzle-orm/EffectLogger", { make: effect_Effect.sync(() => ({ logQuery: (_query, _params) => effect_Effect.void })) }) {
	static [__entity_ts.entityKind] = "drizzle-orm/EffectLogger";
	/**
	* The default layer providing a no-op logger.
	*/
	static Default = effect_Layer.effect(EffectLogger, EffectLogger.make);
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
			return effect_Effect.sync(() => logger.logQuery(query, params));
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
		return effect_Layer.succeed(EffectLogger, EffectLogger.fromDrizzle(logger));
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
	static layer = effect_Layer.succeed(EffectLogger, { logQuery: effect_Effect.fn("EffectLogger.logQuery")(function* (query, params) {
		const stringifiedParams = params.map((p) => {
			try {
				return JSON.stringify(p);
			} catch {
				return String(p);
			}
		});
		yield* effect_Effect.log().pipe(effect_Effect.annotateLogs({
			query,
			params: stringifiedParams
		}));
	}) });
};

//#endregion
exports.EffectLogger = EffectLogger;
//# sourceMappingURL=logger.cjs.map