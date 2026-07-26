Object.defineProperty(exports, Symbol.toStringTag, { value: 'Module' });
const require_runtime = require('../../_virtual/_rolldown/runtime.cjs');
let __entity_ts = require("../../entity.cjs");
let __effect_core_query_effect_ts = require("../../effect-core/query-effect.cjs");
let __sqlite_core_query_builders_query_ts = require("../query-builders/query.cjs");

//#region src/sqlite-core/effect/query.ts
var SQLiteEffectRelationalQuery = class extends __sqlite_core_query_builders_query_ts.SQLiteRelationalQuery {
	static [__entity_ts.entityKind] = "SQLiteEffectRelationalQueryV2";
	/** @internal */
	_prepare(prepare = false) {
		const { query, builtQuery } = this._toSQL();
		const mapper = this.dialect.mapperGenerators.relationalRows({
			isFirst: this.mode === "first",
			parseJson: true,
			parseJsonIfString: false,
			rootJsonMappers: false,
			selection: query.selection,
			arrayModeRoot: true
		});
		return this.session.prepareQuery(builtQuery, "arrays", prepare, "all", mapper);
	}
	prepare() {
		return this._prepare(true);
	}
	execute(placeholderValues) {
		return this._prepare().execute(placeholderValues);
	}
};
(0, __effect_core_query_effect_ts.applyEffectWrapper)(SQLiteEffectRelationalQuery);

//#endregion
exports.SQLiteEffectRelationalQuery = SQLiteEffectRelationalQuery;
//# sourceMappingURL=query.cjs.map