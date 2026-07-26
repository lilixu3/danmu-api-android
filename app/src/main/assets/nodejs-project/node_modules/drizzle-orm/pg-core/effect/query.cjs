Object.defineProperty(exports, Symbol.toStringTag, { value: 'Module' });
const require_runtime = require('../../_virtual/_rolldown/runtime.cjs');
const require_pg_core_query_builders_query = require('../query-builders/query.cjs');
let __entity_ts = require("../../entity.cjs");
let __effect_core_query_effect_ts = require("../../effect-core/query-effect.cjs");

//#region src/pg-core/effect/query.ts
var PgEffectRelationalQuery = class extends require_pg_core_query_builders_query.PgRelationalQuery {
	static [__entity_ts.entityKind] = "PgEffectRelationalQueryV2";
	/** @internal */
	_prepare(name, generateName = false) {
		const { query, builtQuery } = this._toSQL();
		const mapper = this.dialect.mapperGenerators.relationalRows({
			isFirst: this.mode === "first",
			parseJson: this.parseJson,
			parseJsonIfString: false,
			rootJsonMappers: false,
			selection: query.selection,
			arrayModeRoot: true
		});
		return this.session.prepareQuery(builtQuery, "arrays", name ?? generateName, mapper);
	}
	prepare(name) {
		return this._prepare(name, true);
	}
	execute(placeholderValues) {
		return this._prepare().execute(placeholderValues);
	}
};
(0, __effect_core_query_effect_ts.applyEffectWrapper)(PgEffectRelationalQuery);

//#endregion
exports.PgEffectRelationalQuery = PgEffectRelationalQuery;
//# sourceMappingURL=query.cjs.map