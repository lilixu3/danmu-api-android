Object.defineProperty(exports, Symbol.toStringTag, { value: 'Module' });
const require_runtime = require('../../_virtual/_rolldown/runtime.cjs');
const require_pg_core_columns_common = require('./common.cjs');
let __entity_ts = require("../../entity.cjs");
let __sql_sql_ts = require("../../sql/sql.cjs");

//#region src/pg-core/columns/date.common.ts
var PgDateColumnBuilder = class extends require_pg_core_columns_common.PgColumnBuilder {
	static [__entity_ts.entityKind] = "PgDateColumnBaseBuilder";
	/**
	* Adds a `default now()` clause to the column definition.
	* Available for date/time column types.
	*/
	defaultNow() {
		return this.default(__sql_sql_ts.sql`now()`);
	}
};

//#endregion
exports.PgDateColumnBuilder = PgDateColumnBuilder;
//# sourceMappingURL=date.common.cjs.map