Object.defineProperty(exports, Symbol.toStringTag, { value: 'Module' });
const require_runtime = require('../../_virtual/_rolldown/runtime.cjs');
const require_pg_core_query_builders_raw = require('../query-builders/raw.cjs');
let __entity_ts = require("../../entity.cjs");
let __effect_core_query_effect_ts = require("../../effect-core/query-effect.cjs");

//#region src/pg-core/effect/raw.ts
var PgEffectRaw = class extends require_pg_core_query_builders_raw.PgRaw {
	static [__entity_ts.entityKind] = "PgEffectRaw";
	constructor(prepared, sql, query) {
		super(prepared, sql, query);
	}
	execute(placeholderValues) {
		return this.prepared.execute(placeholderValues);
	}
	_prepare() {
		return this.prepared;
	}
};
(0, __effect_core_query_effect_ts.applyEffectWrapper)(PgEffectRaw);

//#endregion
exports.PgEffectRaw = PgEffectRaw;
//# sourceMappingURL=raw.cjs.map