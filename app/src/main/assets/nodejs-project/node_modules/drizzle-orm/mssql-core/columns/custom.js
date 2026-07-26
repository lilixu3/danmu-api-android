import { MsSqlColumn, MsSqlColumnBuilder } from "./common.js";
import { entityKind } from "../../entity.js";
import { getColumnNameAndConfig } from "../../utils.js";

//#region src/mssql-core/columns/custom.ts
var MsSqlCustomColumnBuilder = class extends MsSqlColumnBuilder {
	static [entityKind] = "MsSqlCustomColumnBuilder";
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
var MsSqlCustomColumn = class extends MsSqlColumn {
	static [entityKind] = "MsSqlCustomColumn";
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
		const { name, config } = getColumnNameAndConfig(a, b);
		return new MsSqlCustomColumnBuilder(name, config, customTypeParams);
	};
}

//#endregion
export { MsSqlCustomColumn, MsSqlCustomColumnBuilder, customType };
//# sourceMappingURL=custom.js.map