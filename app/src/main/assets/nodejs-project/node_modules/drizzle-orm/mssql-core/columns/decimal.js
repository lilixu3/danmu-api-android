import { MsSqlColumnBuilderWithIdentity, MsSqlColumnWithIdentity } from "./common.js";
import { entityKind } from "../../entity.js";
import { getColumnNameAndConfig } from "../../utils.js";

//#region src/mssql-core/columns/decimal.ts
var MsSqlDecimalBuilder = class extends MsSqlColumnBuilderWithIdentity {
	static [entityKind] = "MsSqlDecimalBuilder";
	constructor(name, config) {
		super(name, "string numeric", "MsSqlDecimal");
		this.config.precision = config?.precision;
		this.config.scale = config?.scale;
	}
	/** @internal */
	build(table) {
		return new MsSqlDecimal(table, this.config);
	}
};
var MsSqlDecimal = class extends MsSqlColumnWithIdentity {
	static [entityKind] = "MsSqlDecimal";
	precision = this.config.precision;
	scale = this.config.scale;
	mapFromDriverValue = (value) => {
		if (typeof value === "string") return value;
		return String(value);
	};
	getSQLType() {
		if (this.precision !== void 0 && this.scale !== void 0) return `decimal(${this.precision},${this.scale})`;
		else if (this.precision === void 0) return "decimal";
		else return `decimal(${this.precision})`;
	}
};
var MsSqlDecimalNumberBuilder = class extends MsSqlColumnBuilderWithIdentity {
	static [entityKind] = "MsSqlDecimalNumberBuilder";
	constructor(name, config) {
		super(name, "number", "MsSqlDecimalNumber");
		this.config.precision = config?.precision;
		this.config.scale = config?.scale;
	}
	/** @internal */
	build(table) {
		return new MsSqlDecimalNumber(table, this.config);
	}
};
var MsSqlDecimalNumber = class extends MsSqlColumnWithIdentity {
	static [entityKind] = "MsSqlDecimalNumber";
	precision = this.config.precision;
	scale = this.config.scale;
	mapFromDriverValue = (value) => {
		if (typeof value === "number") return value;
		return Number(value);
	};
	mapToDriverValue = String;
	getSQLType() {
		if (this.precision !== void 0 && this.scale !== void 0) return `decimal(${this.precision},${this.scale})`;
		else if (this.precision === void 0) return "decimal";
		else return `decimal(${this.precision})`;
	}
};
var MsSqlDecimalBigIntBuilder = class extends MsSqlColumnBuilderWithIdentity {
	static [entityKind] = "MsSqlDecimalBigIntBuilder";
	constructor(name, config) {
		super(name, "bigint int64", "MsSqlDecimalBigInt");
		this.config.precision = config?.precision ?? 18;
		this.config.scale = config?.scale ?? 0;
	}
	/** @internal */
	build(table) {
		return new MsSqlDecimalBigInt(table, this.config);
	}
};
var MsSqlDecimalBigInt = class extends MsSqlColumnWithIdentity {
	static [entityKind] = "MsSqlDecimalBigInt";
	precision = this.config.precision;
	scale = this.config.scale;
	mapFromDriverValue = BigInt;
	mapToDriverValue = String;
	getSQLType() {
		if (this.precision !== void 0 && this.scale !== void 0) return `decimal(${this.precision},${this.scale})`;
		else if (this.precision === void 0) return "decimal";
		else return `decimal(${this.precision})`;
	}
};
function decimal(a, b) {
	const { name, config } = getColumnNameAndConfig(a, b);
	const mode = config?.mode;
	return mode === "number" ? new MsSqlDecimalNumberBuilder(name, config) : mode === "bigint" ? new MsSqlDecimalBigIntBuilder(name, config) : new MsSqlDecimalBuilder(name, config);
}

//#endregion
export { MsSqlDecimal, MsSqlDecimalBigInt, MsSqlDecimalBigIntBuilder, MsSqlDecimalBuilder, MsSqlDecimalNumber, MsSqlDecimalNumberBuilder, decimal };
//# sourceMappingURL=decimal.js.map