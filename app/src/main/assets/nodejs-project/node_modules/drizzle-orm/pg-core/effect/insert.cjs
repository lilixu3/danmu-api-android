Object.defineProperty(exports, Symbol.toStringTag, { value: 'Module' });
const require_runtime = require('../../_virtual/_rolldown/runtime.cjs');
const require_pg_core_utils = require('../utils.cjs');
const require_pg_core_query_builders_insert = require('../query-builders/insert.cjs');
let __entity_ts = require("../../entity.cjs");
let effect_Effect = require("effect/Effect");
effect_Effect = require_runtime.__toESM(effect_Effect);
let __effect_core_query_effect_ts = require("../../effect-core/query-effect.cjs");

//#region src/pg-core/effect/insert.ts
var PgEffectInsertBase = class extends require_pg_core_query_builders_insert.PgInsertBase {
	static [__entity_ts.entityKind] = "PgEffectInsert";
	/** @internal */
	_prepare(name, generateName = false) {
		const { session, config, dialect } = this;
		const { returning: fields } = config;
		const query = dialect.sqlToQuery(this.getSQL());
		const mapper = fields ? this.dialect.mapperGenerators.rows(fields, void 0) : void 0;
		return session.prepareQuery(query, fields ? "arrays" : "raw", name ?? generateName, mapper, {
			type: "insert",
			tables: [...require_pg_core_utils.extractUsedTable(this.config.table)]
		});
	}
	prepare(name) {
		return this._prepare(name, true);
	}
	execute = (placeholderValues) => {
		return this._prepare().execute(placeholderValues).pipe(effect_Effect.withSpan("drizzle.operation"));
	};
};
(0, __effect_core_query_effect_ts.applyEffectWrapper)(PgEffectInsertBase);

//#endregion
exports.PgEffectInsertBase = PgEffectInsertBase;
//# sourceMappingURL=insert.cjs.map