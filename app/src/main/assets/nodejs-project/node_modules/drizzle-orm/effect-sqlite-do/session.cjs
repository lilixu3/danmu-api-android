Object.defineProperty(exports, Symbol.toStringTag, { value: 'Module' });
const require_runtime = require('../_virtual/_rolldown/runtime.cjs');
let __entity_ts = require("../entity.cjs");
let __errors_ts = require("../errors.cjs");
let effect_Effect = require("effect/Effect");
effect_Effect = require_runtime.__toESM(effect_Effect);
let __sqlite_core_effect_session_ts = require("../sqlite-core/effect/session.cjs");
let effect_Exit = require("effect/Exit");
effect_Exit = require_runtime.__toESM(effect_Exit);

//#region src/effect-sqlite-do/session.ts
var EffectSQLiteDOSession = class extends __sqlite_core_effect_session_ts.SQLiteEffectSession {
	static [__entity_ts.entityKind] = "EffectSQLiteDOSession";
	constructor(client, dialect, relations, options) {
		super(dialect);
		this.client = client;
		this.relations = relations;
		this.options = options;
	}
	prepareQuery(query, mode, _prepare, executeMethod, mapper, queryMetadata, cacheConfig) {
		return new __sqlite_core_effect_session_ts.SQLiteEffectPreparedQuery(executeMethod, {
			all: (params) => {
				const q = this.client.unsafe(query.sql, params);
				if (mode === "arrays") return q.values;
				return q.withoutTransform;
			},
			get: (params) => {
				const q = this.client.unsafe(query.sql, params);
				if (mode === "arrays") return q.values.pipe(effect_Effect.map((e) => e[0]));
				return q.withoutTransform.pipe(effect_Effect.map((e) => e[0]));
			},
			values: (params) => this.client.unsafe(query.sql, params).values,
			run: (params) => this.client.unsafe(query.sql, params).raw
		}, query, mapper, mode, this.options.logger, this.options.cache, queryMetadata, cacheConfig);
	}
	transaction(transaction) {
		const { dialect, relations, options: { storage } } = this;
		return effect_Effect.gen({ self: this }, function* () {
			const context = yield* effect_Effect.context();
			let cause;
			try {
				return storage.transactionSync(() => {
					const tx = new EffectSQLiteDOTransaction(dialect, this, relations);
					const exit = effect_Effect.runSyncExit(effect_Effect.provideContext(transaction(tx), context));
					if (effect_Exit.isFailure(exit)) {
						cause = exit.cause;
						throw new __errors_ts.TransactionRollbackError();
					}
					return exit.value;
				});
			} catch (e) {
				if (cause) return yield* effect_Effect.failCause(cause);
				throw e;
			}
		});
	}
};
var EffectSQLiteDOTransaction = class extends __sqlite_core_effect_session_ts.SQLiteEffectTransaction {
	static [__entity_ts.entityKind] = "EffectSQLiteDOTransaction";
	transaction(transaction) {
		return this.session.transaction(transaction);
	}
};

//#endregion
exports.EffectSQLiteDOSession = EffectSQLiteDOSession;
exports.EffectSQLiteDOTransaction = EffectSQLiteDOTransaction;
//# sourceMappingURL=session.cjs.map