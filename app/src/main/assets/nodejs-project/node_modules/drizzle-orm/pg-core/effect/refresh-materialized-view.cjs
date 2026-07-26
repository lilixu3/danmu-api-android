Object.defineProperty(exports, Symbol.toStringTag, { value: 'Module' });
const require_runtime = require('../../_virtual/_rolldown/runtime.cjs');
const require_pg_core_query_builders_refresh_materialized_view = require('../query-builders/refresh-materialized-view.cjs');
let __entity_ts = require("../../entity.cjs");
let __effect_core_query_effect_ts = require("../../effect-core/query-effect.cjs");

//#region src/pg-core/effect/refresh-materialized-view.ts
var PgEffectRefreshMaterializedView = class extends require_pg_core_query_builders_refresh_materialized_view.PgRefreshMaterializedView {
	static [__entity_ts.entityKind] = "PgEffectRefreshMaterializedView";
	/** @internal */
	_prepare(name, generateName = false) {
		const query = this.dialect.sqlToQuery(this.getSQL());
		return this.session.prepareQuery(query, "raw", name ?? generateName);
	}
	prepare(name) {
		return this._prepare(name, true);
	}
	execute = (placeholderValues) => {
		return this._prepare().execute(placeholderValues);
	};
};
(0, __effect_core_query_effect_ts.applyEffectWrapper)(PgEffectRefreshMaterializedView);

//#endregion
exports.PgEffectRefreshMaterializedView = PgEffectRefreshMaterializedView;
//# sourceMappingURL=refresh-materialized-view.cjs.map