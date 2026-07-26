import { CockroachColumn, CockroachColumnWithArrayBuilder } from "./common.js";
import { entityKind } from "../../entity.js";
import { getColumnNameAndConfig } from "../../utils.js";

//#region src/cockroach-core/columns/decimal.ts
var CockroachDecimalBuilder = class extends CockroachColumnWithArrayBuilder {
	static [entityKind] = "CockroachDecimalBuilder";
	constructor(name, precision, scale) {
		super(name, "string numeric", "CockroachDecimal");
		this.config.precision = precision;
		this.config.scale = scale;
	}
	/** @internal */
	build(table) {
		return new CockroachDecimal(table, this.config);
	}
};
var CockroachDecimal = class extends CockroachColumn {
	static [entityKind] = "CockroachDecimal";
	precision;
	scale;
	constructor(table, config) {
		super(table, config);
		this.precision = config.precision;
		this.scale = config.scale;
	}
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
var CockroachDecimalNumberBuilder = class extends CockroachColumnWithArrayBuilder {
	static [entityKind] = "CockroachDecimalNumberBuilder";
	constructor(name, precision, scale) {
		super(name, "number", "CockroachDecimalNumber");
		this.config.precision = precision;
		this.config.scale = scale;
	}
	/** @internal */
	build(table) {
		return new CockroachDecimalNumber(table, this.config);
	}
};
var CockroachDecimalNumber = class extends CockroachColumn {
	static [entityKind] = "CockroachDecimalNumber";
	precision;
	scale;
	constructor(table, config) {
		super(table, config);
		this.precision = config.precision;
		this.scale = config.scale;
	}
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
var CockroachDecimalBigIntBuilder = class extends CockroachColumnWithArrayBuilder {
	static [entityKind] = "CockroachDecimalBigIntBuilder";
	constructor(name, precision, scale) {
		super(name, "bigint int64", "CockroachDecimalBigInt");
		this.config.precision = precision;
		this.config.scale = scale;
	}
	/** @internal */
	build(table) {
		return new CockroachDecimalBigInt(table, this.config);
	}
};
var CockroachDecimalBigInt = class extends CockroachColumn {
	static [entityKind] = "CockroachDecimalBigInt";
	precision;
	scale;
	constructor(table, config) {
		super(table, config);
		this.precision = config.precision;
		this.scale = config.scale;
	}
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
	return mode === "number" ? new CockroachDecimalNumberBuilder(name, config?.precision, config?.scale) : mode === "bigint" ? new CockroachDecimalBigIntBuilder(name, config?.precision, config?.scale) : new CockroachDecimalBuilder(name, config?.precision, config?.scale);
}
const numeric = decimal;

//#endregion
export { CockroachDecimal, CockroachDecimalBigInt, CockroachDecimalBigIntBuilder, CockroachDecimalBuilder, CockroachDecimalNumber, CockroachDecimalNumberBuilder, decimal, numeric };
//# sourceMappingURL=decimal.js.map