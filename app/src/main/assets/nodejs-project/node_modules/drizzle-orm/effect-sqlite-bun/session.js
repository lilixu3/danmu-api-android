import { entityKind } from "../entity.js";
import * as Effect from "effect/Effect";
import { SQLiteEffectPreparedQuery, SQLiteEffectSession, SQLiteEffectTransaction } from "../sqlite-core/effect/session.js";

//#region src/effect-sqlite-bun/session.ts
var EffectSQLiteBunSession = class extends SQLiteEffectSession {
	static [entityKind] = "EffectSQLiteBunSession";
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
		const { dialect, relations } = this;
		return this.client.withTransaction(Effect.gen({ self: this }, function* () {
			return yield* transaction(new EffectSQLiteBunTransaction(dialect, this, relations));
		}));
	}
};
var EffectSQLiteBunTransaction = class extends SQLiteEffectTransaction {
	static [entityKind] = "EffectSQLiteBunTransaction";
	transaction(transaction) {
		return this.session.transaction(transaction);
	}
};

//#endregion
export { EffectSQLiteBunSession, EffectSQLiteBunTransaction };
//# sourceMappingURL=session.js.map