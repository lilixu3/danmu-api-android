import { CockroachColumn, CockroachColumnWithArrayBuilder } from "./common.js";
import { entityKind } from "../../entity.js";
import { getColumnNameAndConfig } from "../../utils.js";

//#region src/cockroach-core/columns/custom.ts
var CockroachCustomColumnBuilder = class extends CockroachColumnWithArrayBuilder {
	static [entityKind] = "CockroachCustomColumnBuilder";
	constructor(name, fieldConfig, customTypeParams) {
		super(name, "custom", "CockroachCustomColumn");
		this.config.fieldConfig = fieldConfig;
		this.config.customTypeParams = customTypeParams;
	}
	/** @internal */
	build(table) {
		return new CockroachCustomColumn(table, this.config);
	}
};
var CockroachCustomColumn = class extends CockroachColumn {
	static [entityKind] = "CockroachCustomColumn";
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
	jsonSelectIdentifier(identifier, sql, arrayDimensions) {
		if (typeof this.forJsonSelect === "function") return this.forJsonSelect(identifier, sql, arrayDimensions);
		const rawType = this.getSQLType().toLowerCase();
		const parenPos = rawType.indexOf("(");
		switch (parenPos + 1 ? rawType.slice(0, parenPos) : rawType) {
			case "geometry":
			case "timestamp":
			case "decimal":
			case "int8": {
				const arrVal = "[]".repeat(arrayDimensions ?? 0);
				return sql`${identifier}::text${sql.raw(arrVal).if(arrayDimensions)}`;
			}
			default: return identifier;
		}
	}
	mapToDriverValue = (value) => {
		return typeof this.mapTo === "function" ? this.mapTo(value) : value;
	};
};
/**
* Custom cockroach database data type generator
*/
function customType(customTypeParams) {
	return (a, b) => {
		const { name, config } = getColumnNameAndConfig(a, b);
		return new CockroachCustomColumnBuilder(name, config, customTypeParams);
	};
}

//#endregion
export { CockroachCustomColumn, CockroachCustomColumnBuilder, customType };
//# sourceMappingURL=custom.js.map