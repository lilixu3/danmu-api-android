Object.defineProperty(exports, Symbol.toStringTag, { value: 'Module' });
const require_runtime = require('../../_virtual/_rolldown/runtime.cjs');
const require_mssql_core_columns_common = require('./common.cjs');
let __entity_ts = require("../../entity.cjs");
let __utils_ts = require("../../utils.cjs");

//#region src/mssql-core/columns/custom.ts
var MsSqlCustomColumnBuilder = class extends require_mssql_core_columns_common.MsSqlColumnBuilder {
	static [__entity_ts.entityKind] = "MsSqlCustomColumnBuilder";
	constructor(name, fieldConfig, customTypeParams) {
		super(name, "custom", "MsSqlCustomColumn");
		this.config.fieldConfig = fieldConfig;
		this.config.customTypeParams = customTypeParams;
	}
	/** @internal */
	build(table) {
		return new MsSqlCustomColumn(table, this.config);
	}
};
var MsSqlCustomColumn = class extends require_mssql_core_columns_common.MsSqlColumn {
	static [__entity_ts.entityKind] = "MsSqlCustomColumn";
	sqlName;
	mapTo;
	mapFrom;
	mapJson;
	forJsonSelect;
	constructor(table, config) {
		super(table, config);
		this.sqlName = config.customTypeParams.dataType(config.fieldConfig);
		this.mapTo = config.customTypeParams.toDriver;
		this.mapFrom = config.customTypeParams.fromDriver;
		this.mapJson = config.customTypeParams.fromJson;
		this.forJsonSelect = config.customTypeParams.forJsonSelect;
	}
	getSQLType() {
		return this.sqlName;
	}
	mapFromDriverValue = (value) => {
		return typeof this.mapFrom === "function" ? this.mapFrom(value) : value;
	};
	mapFromJsonValue(value) {
		return typeof this.mapJson === "function" ? this.mapJson(value) : this.mapFromDriverValue(value);
	}
	jsonSelectIdentifier(identifier, sql) {
		if (typeof this.forJsonSelect === "function") return this.forJsonSelect(identifier, sql);
		const rawType = this.getSQLType().toLowerCase();
		const parenPos = rawType.indexOf("(");
		switch (parenPos + 1 ? rawType.slice(0, parenPos) : rawType) {
			case "binary":
			case "varbinary":
			case "time":
			case "datetime":
			case "datetime2":
			case "decimal":
			case "float":
			case "bigint": return sql`cast(${identifier} as char)`;
			default: return identifier;
		}
	}
	mapToDriverValue = (value) => {
		return typeof this.mapTo === "function" ? this.mapTo(value) : value;
	};
};
/**
* Custom mssql database data type generator
*/
function customType(customTypeParams) {
	return (a, b) => {
		const { name, config } = (0, __utils_ts.getColumnNameAndConfig)(a, b);
		return new MsSqlCustomColumnBuilder(name, config, customTypeParams);
	};
}

//#endregion
exports.MsSqlCustomColumn = MsSqlCustomColumn;
exports.MsSqlCustomColumnBuilder = MsSqlCustomColumnBuilder;
exports.customType = customType;
//# sourceMappingURL=custom.cjs.map