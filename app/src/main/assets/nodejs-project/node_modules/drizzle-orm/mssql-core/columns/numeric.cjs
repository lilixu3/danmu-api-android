Object.defineProperty(exports, Symbol.toStringTag, { value: 'Module' });
const require_runtime = require('../../_virtual/_rolldown/runtime.cjs');
const require_mssql_core_columns_common = require('./common.cjs');
let __entity_ts = require("../../entity.cjs");
let __utils_ts = require("../../utils.cjs");

//#region src/mssql-core/columns/numeric.ts
var MsSqlNumericBuilder = class extends require_mssql_core_columns_common.MsSqlColumnBuilderWithIdentity {
	static [__entity_ts.entityKind] = "MsSqlNumericBuilder";
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
var MsSqlNumeric = class extends require_mssql_core_columns_common.MsSqlColumnWithIdentity {
	static [__entity_ts.entityKind] = "MsSqlNumeric";
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
var MsSqlNumericNumberBuilder = class extends require_mssql_core_columns_common.MsSqlColumnBuilderWithIdentity {
	static [__entity_ts.entityKind] = "MsSqlNumericNumberBuilder";
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
var MsSqlNumericNumber = class extends require_mssql_core_columns_common.MsSqlColumnWithIdentity {
	static [__entity_ts.entityKind] = "MsSqlNumericNumber";
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
var MsSqlNumericBigIntBuilder = class extends require_mssql_core_columns_common.MsSqlColumnBuilderWithIdentity {
	static [__entity_ts.entityKind] = "MsSqlNumericBigIntBuilder";
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
var MsSqlNumericBigInt = class extends require_mssql_core_columns_common.MsSqlColumnWithIdentity {
	static [__entity_ts.entityKind] = "MsSqlNumericBigInt";
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
	const { name, config } = (0, __utils_ts.getColumnNameAndConfig)(a, b);
	const mode = config?.mode;
	return mode === "number" ? new MsSqlNumericNumberBuilder(name, config) : mode === "bigint" ? new MsSqlNumericBigIntBuilder(name, config) : new MsSqlNumericBuilder(name, config);
}

//#endregion
exports.MsSqlNumeric = MsSqlNumeric;
exports.MsSqlNumericBigInt = MsSqlNumericBigInt;
exports.MsSqlNumericBigIntBuilder = MsSqlNumericBigIntBuilder;
exports.MsSqlNumericBuilder = MsSqlNumericBuilder;
exports.MsSqlNumericNumber = MsSqlNumericNumber;
exports.MsSqlNumericNumberBuilder = MsSqlNumericNumberBuilder;
exports.numeric = numeric;
//# sourceMappingURL=numeric.cjs.map