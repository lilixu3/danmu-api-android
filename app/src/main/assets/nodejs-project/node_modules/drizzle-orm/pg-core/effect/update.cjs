Object.defineProperty(exports, Symbol.toStringTag, { value: 'Module' });
const require_runtime = require('../../_virtual/_rolldown/runtime.cjs');
const require_pg_core_utils = require('../utils.cjs');
const require_pg_core_query_builders_update = require('../query-builders/update.cjs');
let __entity_ts = require("../../entity.cjs");
let __effect_core_query_effect_ts = require("../../effect-core/query-effect.cjs");

//#region src/pg-core/effect/update.ts
var PgEffectUpdateBase = class extends require_pg_core_query_builders_update.PgUpdateBase {
	static [__entity_ts.entityKind] = "PgEffectUpdate";
	/** @internal */
	_prepare(name, generateName = false) {
		const { session, config, dialect, joinsNotNullableMap } = this;
		const { returning: fields } = config;
		const query = dialect.sqlToQuery(this.getSQL());
		const mapper = fields ? this.dialect.mapperGenerators.rows(fields, joinsNotNullableMap) : void 0;
		return session.prepareQuery(query, fields ? "arrays" : "raw", name ?? generateName, mapper, {
			type: "update",
			tables: [...require_pg_core_utils.extractUsedTable(this.config.table)]
		});
	}
	prepare(name) {
		return this._prepare(name, true);
	}
	execute = (placeholderValues = {}) => {
		return this._prepare().execute(placeholderValues);
	};
};
(0, __effect_core_query_effect_ts.applyEffectWrapper)(PgEffectUpdateBase);

//#endregion
exports.PgEffectUpdateBase = PgEffectUpdateBase;
//# sourceMappingURL=update.cjs.map