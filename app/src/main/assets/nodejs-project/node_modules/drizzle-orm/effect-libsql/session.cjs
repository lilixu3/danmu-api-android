Object.defineProperty(exports, Symbol.toStringTag, { value: 'Module' });
const require_runtime = require('../_virtual/_rolldown/runtime.cjs');
let __entity_ts = require("../entity.cjs");
let effect_Effect = require("effect/Effect");
effect_Effect = require_runtime.__toESM(effect_Effect);
let __sqlite_core_effect_session_ts = require("../sqlite-core/effect/session.cjs");

//#region src/effect-libsql/session.ts
var EffectLibsqlSession = class extends __sqlite_core_effect_session_ts.SQLiteEffectSession {
	static [__entity_ts.entityKind] = "EffectLibsqlSession";
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
		const { dialect, relations } = this;
		return this.client.withTransaction(effect_Effect.gen({ self: this }, function* () {
			return yield* transaction(new EffectLibsqlTransaction(dialect, this, relations));
		}));
	}
};
var EffectLibsqlTransaction = class extends __sqlite_core_effect_session_ts.SQLiteEffectTransaction {
	static [__entity_ts.entityKind] = "EffectLibsqlTransaction";
	transaction(transaction) {
		return this.session.transaction(transaction);
	}
};

//#endregion
exports.EffectLibsqlSession = EffectLibsqlSession;
exports.EffectLibsqlTransaction = EffectLibsqlTransaction;
//# sourceMappingURL=session.cjs.map