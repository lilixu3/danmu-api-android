import { entityKind } from "../entity.js";
import { TransactionRollbackError } from "../errors.js";
import * as Effect from "effect/Effect";
import { SQLiteEffectPreparedQuery, SQLiteEffectSession, SQLiteEffectTransaction } from "../sqlite-core/effect/session.js";
import * as Exit from "effect/Exit";

//#region src/effect-sqlite-do/session.ts
var EffectSQLiteDOSession = class extends SQLiteEffectSession {
	static [entityKind] = "EffectSQLiteDOSession";
	constructor(client, dialect, relations, options) {
		super(dialect);
		this.client = client;
		this.relations = relations;
		this.options = options;
	}
	prepareQuery(query, mode, _prepare, executeMethod, mapper, queryMetadata, cacheConfig) {
		return new SQLiteEffectPreparedQuery(executeMethod, {
			all: (params) => {
				const q = this.client.unsafe(query.sql, params);
				if (mode === "arrays") return q.values;
				return q.withoutTransform;
			},
			get: (params) => {
				const q = this.client.unsafe(query.sql, params);
				if (mode === "arrays") return q.values.pipe(Effect.map((e) => e[0]));
				return q.withoutTransform.pipe(Effect.map((e) => e[0]));
			},
			values: (params) => this.client.unsafe(query.sql, params).values,
			run: (params) => this.client.unsafe(query.sql, params).raw
		}, query, mapper, mode, this.options.logger, this.options.cache, queryMetadata, cacheConfig);
	}
	transaction(transaction) {
		const { dialect, relations, options: { storage } } = this;
		return Effect.gen({ self: this }, function* () {
			const context = yield* Effect.context();
			let cause;
			try {
				return storage.transactionSync(() => {
					const tx = new EffectSQLiteDOTransaction(dialect, this, relations);
					const exit = Effect.runSyncExit(Effect.provideContext(transaction(tx), context));
					if (Exit.isFailure(exit)) {
						cause = exit.cause;
						throw new TransactionRollbackError();
					}
					return exit.value;
				});
			} catch (e) {
				if (cause) return yield* Effect.failCause(cause);
				throw e;
			}
		});
	}
};
var EffectSQLiteDOTransaction = class extends SQLiteEffectTransaction {
	static [entityKind] = "EffectSQLiteDOTransaction";
	transaction(transaction) {
		return this.session.transaction(transaction);
	}
};

//#endregion
export { EffectSQLiteDOSession, EffectSQLiteDOTransaction };
//# sourceMappingURL=session.js.map