Object.defineProperty(exports, Symbol.toStringTag, { value: 'Module' });
const require_runtime = require('../../_virtual/_rolldown/runtime.cjs');
const require_pg_core_query_builders_count = require('../query-builders/count.cjs');
let __entity_ts = require("../../entity.cjs");
let __effect_core_query_effect_ts = require("../../effect-core/query-effect.cjs");

//#region src/pg-core/effect/count.ts
var PgEffectCountBuilder = class extends require_pg_core_query_builders_count.PgCountBuilder {
	static [__entity_ts.entityKind] = "PgEffectCountBuilder";
	session;
	constructor({ source, dialect, filters, session }) {
		super({
			source,
			dialect,
			filters
		});
		this.session = session;
	}
	execute(placeholderValues) {
		return this.session.prepareQuery(this.build(), "arrays", false, (rows) => {
			const v = rows[0]?.[0];
			if (typeof v === "number") return v;
			return v ? Number(v) : 0;
		}).execute(placeholderValues);
	}
};
(0, __effect_core_query_effect_ts.applyEffectWrapper)(PgEffectCountBuilder);

//#endregion
exports.PgEffectCountBuilder = PgEffectCountBuilder;
//# sourceMappingURL=count.cjs.map