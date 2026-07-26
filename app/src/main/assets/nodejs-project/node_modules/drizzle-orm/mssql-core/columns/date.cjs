Object.defineProperty(exports, Symbol.toStringTag, { value: 'Module' });
const require_runtime = require('../../_virtual/_rolldown/runtime.cjs');
const require_mssql_core_columns_common = require('./common.cjs');
const require_mssql_core_columns_date_common = require('./date.common.cjs');
let __entity_ts = require("../../entity.cjs");
let __utils_ts = require("../../utils.cjs");

//#region src/mssql-core/columns/date.ts
var MsSqlDateBuilder = class extends require_mssql_core_columns_date_common.MsSqlDateColumnBaseBuilder {
	static [__entity_ts.entityKind] = "MsSqlDateBuilder";
	constructor(name) {
		super(name, "object date", "MsSqlDate");
	}
	/** @internal */
	build(table) {
		return new MsSqlDate(table, this.config);
	}
};
var MsSqlDate = class extends require_mssql_core_columns_common.MsSqlColumn {
	static [__entity_ts.entityKind] = "MsSqlDate";
	constructor(table, config) {
		super(table, config);
	}
	getSQLType() {
		return `date`;
	}
	mapFromDriverValue = (value) => {
		return new Date(value);
	};
};
var MsSqlDateStringBuilder = class extends require_mssql_core_columns_date_common.MsSqlDateColumnBaseBuilder {
	static [__entity_ts.entityKind] = "MsSqlDateStringBuilder";
	constructor(name) {
		super(name, "string date", "MsSqlDateString");
	}
	/** @internal */
	build(table) {
		return new MsSqlDateString(table, this.config);
	}
};
var MsSqlDateString = class extends require_mssql_core_columns_common.MsSqlColumn {
	static [__entity_ts.entityKind] = "MsSqlDateString";
	constructor(table, config) {
		super(table, config);
	}
	getSQLType() {
		return `date`;
	}
	mapFromDriverValue = (value) => {
		return typeof value === "string" ? value : value?.toISOString().split("T")[0] ?? null;
	};
};
function date(a, b) {
	const { name, config } = (0, __utils_ts.getColumnNameAndConfig)(a, b);
	if (config?.mode === "string") return new MsSqlDateStringBuilder(name);
	return new MsSqlDateBuilder(name);
}

//#endregion
exports.MsSqlDate = MsSqlDate;
exports.MsSqlDateBuilder = MsSqlDateBuilder;
exports.MsSqlDateString = MsSqlDateString;
exports.MsSqlDateStringBuilder = MsSqlDateStringBuilder;
exports.date = date;
//# sourceMappingURL=date.cjs.map