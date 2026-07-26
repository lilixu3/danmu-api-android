Object.defineProperty(exports, Symbol.toStringTag, { value: 'Module' });
const require_runtime = require('../../_virtual/_rolldown/runtime.cjs');
const require_pg_core_query_builders_count = require('../query-builders/count.cjs');
let __entity_ts = require("../../entity.cjs");
let __utils_ts = require("../../utils.cjs");
let __query_promise_ts = require("../../query-promise.cjs");

//#region src/pg-core/async/count.ts
var PgAsyncCountBuilder = class extends require_pg_core_query_builders_count.PgCountBuilder {
	static [__entity_ts.entityKind] = "PgAsyncCountBuilder";
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
(0, __utils_ts.applyMixins)(PgAsyncCountBuilder, [__query_promise_ts.QueryPromise]);

//#endregion
exports.PgAsyncCountBuilder = PgAsyncCountBuilder;
//# sourceMappingURL=count.cjs.map