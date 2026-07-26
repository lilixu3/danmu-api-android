Object.defineProperty(exports, Symbol.toStringTag, { value: 'Module' });
const require_runtime = require('../../_virtual/_rolldown/runtime.cjs');
let __entity_ts = require("../../entity.cjs");
let __effect_core_query_effect_ts = require("../../effect-core/query-effect.cjs");
let __sqlite_core_query_builders_count_ts = require("../query-builders/count.cjs");

//#region src/sqlite-core/effect/count.ts
var SQLiteEffectCountBuilder = class extends __sqlite_core_query_builders_count_ts.SQLiteCountBuilder {
	static [__entity_ts.entityKind] = "SQLiteEffectCountBuilder";
	constructor(countConfig) {
		super(countConfig);
	}
	execute(placeholderValues) {
		return this.session.prepareQuery(this.build(), "arrays", false, "all", (rows) => {
			const v = rows[0]?.[0];
			if (typeof v === "number") return v;
			return v ? Number(v) : 0;
		}).execute(placeholderValues);
	}
};
(0, __effect_core_query_effect_ts.applyEffectWrapper)(SQLiteEffectCountBuilder);

//#endregion
exports.SQLiteEffectCountBuilder = SQLiteEffectCountBuilder;
//# sourceMappingURL=count.cjs.map