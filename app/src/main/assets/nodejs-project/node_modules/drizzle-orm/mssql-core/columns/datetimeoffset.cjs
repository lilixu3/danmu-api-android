Object.defineProperty(exports, Symbol.toStringTag, { value: 'Module' });
const require_runtime = require('../../_virtual/_rolldown/runtime.cjs');
const require_mssql_core_columns_common = require('./common.cjs');
const require_mssql_core_columns_date_common = require('./date.common.cjs');
let __entity_ts = require("../../entity.cjs");
let __utils_ts = require("../../utils.cjs");

//#region src/mssql-core/columns/datetimeoffset.ts
var MsSqlDateTimeOffsetBuilder = class extends require_mssql_core_columns_date_common.MsSqlDateColumnBaseBuilder {
	static [__entity_ts.entityKind] = "MsSqlDateTimeOffsetBuilder";
	constructor(name, config) {
		super(name, "object date", "MsSqlDateTimeOffset");
		this.config.precision = config?.precision;
	}
	/** @internal */
	build(table) {
		return new MsSqlDateTimeOffset(table, this.config);
	}
};
var MsSqlDateTimeOffset = class extends require_mssql_core_columns_common.MsSqlColumn {
	static [__entity_ts.entityKind] = "MsSqlDateTimeOffset";
	precision;
	constructor(table, config) {
		super(table, config);
		this.precision = config.precision;
	}
	getSQLType() {
		return `datetimeoffset${this.precision === void 0 ? "" : `(${this.precision})`}`;
	}
};
var MsSqlDateTimeOffsetStringBuilder = class extends require_mssql_core_columns_date_common.MsSqlDateColumnBaseBuilder {
	static [__entity_ts.entityKind] = "MsSqlDateTimeOffsetStringBuilder";
	constructor(name, config) {
		super(name, "string datetime", "MsSqlDateTimeOffsetString");
		this.config.precision = config?.precision;
	}
	/** @internal */
	build(table) {
		return new MsSqlDateTimeOffsetString(table, this.config);
	}
};
var MsSqlDateTimeOffsetString = class extends require_mssql_core_columns_common.MsSqlColumn {
	static [__entity_ts.entityKind] = "MsSqlDateTimeOffsetString";
	precision;
	constructor(table, config) {
		super(table, config);
		this.precision = config.precision;
	}
	getSQLType() {
		return `datetimeoffset${this.precision === void 0 ? "" : `(${this.precision})`}`;
	}
	mapFromDriverValue = (value) => {
		return typeof value === "string" ? value : value?.toISOString() ?? null;
	};
};
function datetimeoffset(a, b) {
	const { name, config } = (0, __utils_ts.getColumnNameAndConfig)(a, b);
	if (config?.mode === "string") return new MsSqlDateTimeOffsetStringBuilder(name, config);
	return new MsSqlDateTimeOffsetBuilder(name, config);
}

//#endregion
exports.MsSqlDateTimeOffset = MsSqlDateTimeOffset;
exports.MsSqlDateTimeOffsetBuilder = MsSqlDateTimeOffsetBuilder;
exports.MsSqlDateTimeOffsetString = MsSqlDateTimeOffsetString;
exports.MsSqlDateTimeOffsetStringBuilder = MsSqlDateTimeOffsetStringBuilder;
exports.datetimeoffset = datetimeoffset;
//# sourceMappingURL=datetimeoffset.cjs.map