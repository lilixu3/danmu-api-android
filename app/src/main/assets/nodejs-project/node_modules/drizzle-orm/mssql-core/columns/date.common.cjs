Object.defineProperty(exports, Symbol.toStringTag, { value: 'Module' });
const require_runtime = require('../../_virtual/_rolldown/runtime.cjs');
const require_mssql_core_columns_common = require('./common.cjs');
let __entity_ts = require("../../entity.cjs");
let __sql_sql_ts = require("../../sql/sql.cjs");

//#region src/mssql-core/columns/date.common.ts
var MsSqlDateColumnBaseBuilder = class extends require_mssql_core_columns_common.MsSqlColumnBuilder {
	static [__entity_ts.entityKind] = "MsSqlDateColumnBuilder";
	defaultGetDate() {
		return this.default(__sql_sql_ts.sql`(getdate())`);
	}
};

//#endregion
exports.MsSqlDateColumnBaseBuilder = MsSqlDateColumnBaseBuilder;
//# sourceMappingURL=date.common.cjs.map