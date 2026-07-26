import { MsSqlColumnBuilderWithIdentity, MsSqlColumnWithIdentity } from "./common.js";
import { entityKind } from "../../entity.js";
import { getColumnNameAndConfig } from "../../utils.js";

//#region src/mssql-core/columns/numeric.ts
var MsSqlNumericBuilder = class extends MsSqlColumnBuilderWithIdentity {
	static [entityKind] = "MsSqlNumericBuilder";
	constructor(name, config) {
		super(name, "string numeric", "MsSqlNumeric");
		this.config.precision = config?.precision;
		this.config.scale = config?.scale;
	}
	/** @internal */
	build(table) {
		return new MsSqlNumeric(table, this.config);
	}
};
var MsSqlNumeric = class extends MsSqlColumnWithIdentity {
	static [entityKind] = "MsSqlNumeric";
	precision = this.config.precision;
	scale = this.config.scale;
	mapFromDriverValue = (value) => {
		if (typeof value === "string") return value;
		return String(value);
	};
	getSQLType() {
		if (this.precision !== void 0 && this.scale !== void 0) return `numeric(${this.precision},${this.scale})`;
		else if (this.precision === void 0) return "numeric";
		else return `numeric(${this.precision})`;
	}
};
var MsSqlNumericNumberBuilder = class extends MsSqlColumnBuilderWithIdentity {
	static [entityKind] = "MsSqlNumericNumberBuilder";
	constructor(name, config) {
		super(name, "number", "MsSqlNumericNumber");
		this.config.precision = config?.precision;
		this.config.scale = config?.scale;
	}
	/** @internal */
	build(table) {
		return new MsSqlNumericNumber(table, this.config);
	}
};
var MsSqlNumericNumber = class extends MsSqlColumnWithIdentity {
	static [entityKind] = "MsSqlNumericNumber";
	precision = this.config.precision;
	scale = this.config.scale;
	mapFromDriverValue = (value) => {
		if (typeof value === "number") return value;
		return Number(value);
	};
	mapToDriverValue = String;
	getSQLType() {
		if (this.precision !== void 0 && this.scale !== void 0) return `numeric(${this.precision},${this.scale})`;
		else if (this.precision === void 0) return "numeric";
		else return `numeric(${this.precision})`;
	}
};
var MsSqlNumericBigIntBuilder = class extends MsSqlColumnBuilderWithIdentity {
	static [entityKind] = "MsSqlNumericBigIntBuilder";
	constructor(name, config) {
		super(name, "bigint int64", "MsSqlNumericBigInt");
		this.config.precision = config?.precision;
		this.config.scale = config?.scale;
	}
	/** @internal */
	build(table) {
		return new MsSqlNumericBigInt(table, this.config);
	}
};
var MsSqlNumericBigInt = class extends MsSqlColumnWithIdentity {
	static [entityKind] = "MsSqlNumericBigInt";
	precision = this.config.precision;
	scale = this.config.scale;
	mapFromDriverValue = BigInt;
	mapToDriverValue = String;
	getSQLType() {
		if (this.precision !== void 0 && this.scale !== void 0) return `numeric(${this.precision},${this.scale})`;
		else if (this.precision === void 0) return "numeric";
		else return `numeric(${this.precision})`;
	}
};
function numeric(a, b) {
	const { name, config } = getColumnNameAndConfig(a, b);
	const mode = config?.mode;
	return mode === "number" ? new MsSqlNumericNumberBuilder(name, config) : mode === "bigint" ? new MsSqlNumericBigIntBuilder(name, config) : new MsSqlNumericBuilder(name, config);
}

//#endregion
export { MsSqlNumeric, MsSqlNumericBigInt, MsSqlNumericBigIntBuilder, MsSqlNumericBuilder, MsSqlNumericNumber, MsSqlNumericNumberBuilder, numeric };
//# sourceMappingURL=numeric.js.map