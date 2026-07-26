Object.defineProperty(exports, Symbol.toStringTag, { value: 'Module' });
const require_runtime = require('../../_virtual/_rolldown/runtime.cjs');
const require_mssql_core_columns_common = require('./common.cjs');
const require_mssql_core_columns_date_common = require('./date.common.cjs');
let __entity_ts = require("../../entity.cjs");
let __utils_ts = require("../../utils.cjs");

//#region src/mssql-core/columns/datetime.ts
var MsSqlDateTimeBuilder = class extends require_mssql_core_columns_date_common.MsSqlDateColumnBaseBuilder {
	static [__entity_ts.entityKind] = "MsSqlDateTimeBuilder";
	constructor(name) {
		super(name, "object date", "MsSqlDateTime");
	}
	/** @internal */
	build(table) {
		return new MsSqlDateTime(table, this.config);
	}
};
var MsSqlDateTime = class extends require_mssql_core_columns_common.MsSqlColumn {
	static [__entity_ts.entityKind] = "MsSqlDateTime";
	constructor(table, config) {
		super(table, config);
	}
	getSQLType() {
		return `datetime`;
	}
};
var MsSqlDateTimeStringBuilder = class extends require_mssql_core_columns_date_common.MsSqlDateColumnBaseBuilder {
	static [__entity_ts.entityKind] = "MsSqlDateTimeStringBuilder";
	constructor(name) {
		super(name, "string datetime", "MsSqlDateTimeString");
	}
	/** @internal */
	build(table) {
		return new MsSqlDateTimeString(table, this.config);
	}
};
var MsSqlDateTimeString = class extends require_mssql_core_columns_common.MsSqlColumn {
	static [__entity_ts.entityKind] = "MsSqlDateTimeString";
	constructor(table, config) {
		super(table, config);
	}
	getSQLType() {
		return "datetime";
	}
	mapFromDriverValue = (value) => {
		return typeof value === "string" ? value : value?.toISOString() ?? null;
	};
};
function datetime(a, b) {
	const { name, config } = (0, __utils_ts.getColumnNameAndConfig)(a, b);
	if (config?.mode === "string") return new MsSqlDateTimeStringBuilder(name);
	return new MsSqlDateTimeBuilder(name);
}

//#endregion
exports.MsSqlDateTime = MsSqlDateTime;
exports.MsSqlDateTimeBuilder = MsSqlDateTimeBuilder;
exports.MsSqlDateTimeString = MsSqlDateTimeString;
exports.MsSqlDateTimeStringBuilder = MsSqlDateTimeStringBuilder;
exports.datetime = datetime;
//# sourceMappingURL=datetime.cjs.map