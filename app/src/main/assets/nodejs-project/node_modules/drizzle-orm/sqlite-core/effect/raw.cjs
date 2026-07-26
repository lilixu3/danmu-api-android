Object.defineProperty(exports, Symbol.toStringTag, { value: 'Module' });
const require_runtime = require('../../_virtual/_rolldown/runtime.cjs');
let __entity_ts = require("../../entity.cjs");
let __effect_core_query_effect_ts = require("../../effect-core/query-effect.cjs");
let __sqlite_core_query_builders_raw_ts = require("../query-builders/raw.cjs");

//#region src/sqlite-core/effect/raw.ts
var SQLiteEffectRaw = class extends __sqlite_core_query_builders_raw_ts.SQLiteRaw {
	static [__entity_ts.entityKind] = "SQLiteEffectRaw";
	constructor(prepared, sql, query) {
		super(prepared, sql, query);
	}
	execute(placeholderValues) {
		return this.prepared.execute(placeholderValues);
	}
};
(0, __effect_core_query_effect_ts.applyEffectWrapper)(SQLiteEffectRaw);

//#endregion
exports.SQLiteEffectRaw = SQLiteEffectRaw;
//# sourceMappingURL=raw.cjs.map