Object.defineProperty(exports, Symbol.toStringTag, { value: 'Module' });
const require_runtime = require('../../_virtual/_rolldown/runtime.cjs');
const require_mssql_core_columns_common = require('./common.cjs');
let __entity_ts = require("../../entity.cjs");
let __utils_ts = require("../../utils.cjs");

//#region src/mssql-core/columns/decimal.ts
var MsSqlDecimalBuilder = class extends require_mssql_core_columns_common.MsSqlColumnBuilderWithIdentity {
	static [__entity_ts.entityKind] = "MsSqlDecimalBuilder";
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
var MsSqlDecimal = class extends require_mssql_core_columns_common.MsSqlColumnWithIdentity {
	static [__entity_ts.entityKind] = "MsSqlDecimal";
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
var MsSqlDecimalNumberBuilder = class extends require_mssql_core_columns_common.MsSqlColumnBuilderWithIdentity {
	static [__entity_ts.entityKind] = "MsSqlDecimalNumberBuilder";
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
var MsSqlDecimalNumber = class extends require_mssql_core_columns_common.MsSqlColumnWithIdentity {
	static [__entity_ts.entityKind] = "MsSqlDecimalNumber";
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
var MsSqlDecimalBigIntBuilder = class extends require_mssql_core_columns_common.MsSqlColumnBuilderWithIdentity {
	static [__entity_ts.entityKind] = "MsSqlDecimalBigIntBuilder";
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
var MsSqlDecimalBigInt = class extends require_mssql_core_columns_common.MsSqlColumnWithIdentity {
	static [__entity_ts.entityKind] = "MsSqlDecimalBigInt";
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
	const { name, config } = (0, __utils_ts.getColumnNameAndConfig)(a, b);
	const mode = config?.mode;
	return mode === "number" ? new MsSqlDecimalNumberBuilder(name, config) : mode === "bigint" ? new MsSqlDecimalBigIntBuilder(name, config) : new MsSqlDecimalBuilder(name, config);
}

//#endregion
exports.MsSqlDecimal = MsSqlDecimal;
exports.MsSqlDecimalBigInt = MsSqlDecimalBigInt;
exports.MsSqlDecimalBigIntBuilder = MsSqlDecimalBigIntBuilder;
exports.MsSqlDecimalBuilder = MsSqlDecimalBuilder;
exports.MsSqlDecimalNumber = MsSqlDecimalNumber;
exports.MsSqlDecimalNumberBuilder = MsSqlDecimalNumberBuilder;
exports.decimal = decimal;
//# sourceMappingURL=decimal.cjs.map