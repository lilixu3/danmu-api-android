Object.defineProperty(exports, Symbol.toStringTag, { value: 'Module' });
const require_runtime = require('../../_virtual/_rolldown/runtime.cjs');
const require_cockroach_core_columns_common = require('./common.cjs');
let __entity_ts = require("../../entity.cjs");
let __sql_sql_ts = require("../../sql/sql.cjs");

//#region src/cockroach-core/columns/date.common.ts
var CockroachDateColumnBaseBuilder = class extends require_cockroach_core_columns_common.CockroachColumnWithArrayBuilder {
	static [__entity_ts.entityKind] = "CockroachDateColumnBaseBuilder";
	defaultNow() {
		return this.default(__sql_sql_ts.sql`now()`);
	}
};

//#endregion
exports.CockroachDateColumnBaseBuilder = CockroachDateColumnBaseBuilder;
//# sourceMappingURL=date.common.cjs.map