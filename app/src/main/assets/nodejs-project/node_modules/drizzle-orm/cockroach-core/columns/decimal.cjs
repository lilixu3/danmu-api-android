Object.defineProperty(exports, Symbol.toStringTag, { value: 'Module' });
const require_runtime = require('../../_virtual/_rolldown/runtime.cjs');
const require_cockroach_core_columns_common = require('./common.cjs');
let __entity_ts = require("../../entity.cjs");
let __utils_ts = require("../../utils.cjs");

//#region src/cockroach-core/columns/decimal.ts
var CockroachDecimalBuilder = class extends require_cockroach_core_columns_common.CockroachColumnWithArrayBuilder {
	static [__entity_ts.entityKind] = "CockroachDecimalBuilder";
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
var CockroachDecimal = class extends require_cockroach_core_columns_common.CockroachColumn {
	static [__entity_ts.entityKind] = "CockroachDecimal";
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
var CockroachDecimalNumberBuilder = class extends require_cockroach_core_columns_common.CockroachColumnWithArrayBuilder {
	static [__entity_ts.entityKind] = "CockroachDecimalNumberBuilder";
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
var CockroachDecimalNumber = class extends require_cockroach_core_columns_common.CockroachColumn {
	static [__entity_ts.entityKind] = "CockroachDecimalNumber";
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
var CockroachDecimalBigIntBuilder = class extends require_cockroach_core_columns_common.CockroachColumnWithArrayBuilder {
	static [__entity_ts.entityKind] = "CockroachDecimalBigIntBuilder";
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
var CockroachDecimalBigInt = class extends require_cockroach_core_columns_common.CockroachColumn {
	static [__entity_ts.entityKind] = "CockroachDecimalBigInt";
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
	const { name, config } = (0, __utils_ts.getColumnNameAndConfig)(a, b);
	const mode = config?.mode;
	return mode === "number" ? new CockroachDecimalNumberBuilder(name, config?.precision, config?.scale) : mode === "bigint" ? new CockroachDecimalBigIntBuilder(name, config?.precision, config?.scale) : new CockroachDecimalBuilder(name, config?.precision, config?.scale);
}
const numeric = decimal;

//#endregion
exports.CockroachDecimal = CockroachDecimal;
exports.CockroachDecimalBigInt = CockroachDecimalBigInt;
exports.CockroachDecimalBigIntBuilder = CockroachDecimalBigIntBuilder;
exports.CockroachDecimalBuilder = CockroachDecimalBuilder;
exports.CockroachDecimalNumber = CockroachDecimalNumber;
exports.CockroachDecimalNumberBuilder = CockroachDecimalNumberBuilder;
exports.decimal = decimal;
exports.numeric = numeric;
//# sourceMappingURL=decimal.cjs.map