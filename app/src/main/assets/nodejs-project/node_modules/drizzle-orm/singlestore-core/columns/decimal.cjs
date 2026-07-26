Object.defineProperty(exports, Symbol.toStringTag, { value: 'Module' });
const require_runtime = require('../../_virtual/_rolldown/runtime.cjs');
const require_singlestore_core_columns_common = require('./common.cjs');
let __entity_ts = require("../../entity.cjs");
let __utils_ts = require("../../utils.cjs");

//#region src/singlestore-core/columns/decimal.ts
var SingleStoreDecimalBuilder = class extends require_singlestore_core_columns_common.SingleStoreColumnBuilderWithAutoIncrement {
	static [__entity_ts.entityKind] = "SingleStoreDecimalBuilder";
	constructor(name, config) {
		super(name, config?.unsigned ? "string unumeric" : "string numeric", "SingleStoreDecimal");
		this.config.precision = config?.precision;
		this.config.scale = config?.scale;
		this.config.unsigned = config?.unsigned;
	}
	/** @internal */
	build(table) {
		return new SingleStoreDecimal(table, this.config);
	}
};
var SingleStoreDecimal = class extends require_singlestore_core_columns_common.SingleStoreColumnWithAutoIncrement {
	static [__entity_ts.entityKind] = "SingleStoreDecimal";
	precision = this.config.precision;
	scale = this.config.scale;
	unsigned = this.config.unsigned;
	mapFromDriverValue = (value) => {
		if (typeof value === "string") return value;
		return String(value);
	};
	getSQLType() {
		let type = "";
		if (this.precision !== void 0 && this.scale !== void 0) type += `decimal(${this.precision},${this.scale})`;
		else if (this.precision === void 0) type += "decimal";
		else type += `decimal(${this.precision})`;
		type = type === "decimal(10,0)" || type === "decimal(10)" ? "decimal" : type;
		return this.unsigned ? `${type} unsigned` : type;
	}
};
var SingleStoreDecimalNumberBuilder = class extends require_singlestore_core_columns_common.SingleStoreColumnBuilderWithAutoIncrement {
	static [__entity_ts.entityKind] = "SingleStoreDecimalNumberBuilder";
	constructor(name, config) {
		super(name, config?.unsigned ? "number unsigned" : "number", "SingleStoreDecimalNumber");
		this.config.precision = config?.precision;
		this.config.scale = config?.scale;
		this.config.unsigned = config?.unsigned;
	}
	/** @internal */
	build(table) {
		return new SingleStoreDecimalNumber(table, this.config);
	}
};
var SingleStoreDecimalNumber = class extends require_singlestore_core_columns_common.SingleStoreColumnWithAutoIncrement {
	static [__entity_ts.entityKind] = "SingleStoreDecimalNumber";
	precision = this.config.precision;
	scale = this.config.scale;
	unsigned = this.config.unsigned;
	mapFromDriverValue = (value) => {
		if (typeof value === "number") return value;
		return Number(value);
	};
	mapToDriverValue = String;
	getSQLType() {
		let type = "";
		if (this.precision !== void 0 && this.scale !== void 0) type += `decimal(${this.precision},${this.scale})`;
		else if (this.precision === void 0) type += "decimal";
		else type += `decimal(${this.precision})`;
		type = type === "decimal(10,0)" || type === "decimal(10)" ? "decimal" : type;
		return this.unsigned ? `${type} unsigned` : type;
	}
};
var SingleStoreDecimalBigIntBuilder = class extends require_singlestore_core_columns_common.SingleStoreColumnBuilderWithAutoIncrement {
	static [__entity_ts.entityKind] = "SingleStoreDecimalBigIntBuilder";
	constructor(name, config) {
		super(name, config?.unsigned ? "bigint uint64" : "bigint int64", "SingleStoreDecimalBigInt");
		this.config.precision = config?.precision;
		this.config.scale = config?.scale;
		this.config.unsigned = config?.unsigned;
	}
	/** @internal */
	build(table) {
		return new SingleStoreDecimalBigInt(table, this.config);
	}
};
var SingleStoreDecimalBigInt = class extends require_singlestore_core_columns_common.SingleStoreColumnWithAutoIncrement {
	static [__entity_ts.entityKind] = "SingleStoreDecimalBigInt";
	precision = this.config.precision;
	scale = this.config.scale;
	unsigned = this.config.unsigned;
	mapFromDriverValue = BigInt;
	mapToDriverValue = String;
	getSQLType() {
		let type = "";
		if (this.precision !== void 0 && this.scale !== void 0) type += `decimal(${this.precision},${this.scale})`;
		else if (this.precision === void 0) type += "decimal";
		else type += `decimal(${this.precision})`;
		type = type === "decimal(10,0)" || type === "decimal(10)" ? "decimal" : type;
		return this.unsigned ? `${type} unsigned` : type;
	}
};
function decimal(a, b = {}) {
	const { name, config } = (0, __utils_ts.getColumnNameAndConfig)(a, b);
	const mode = config?.mode;
	return mode === "number" ? new SingleStoreDecimalNumberBuilder(name, config) : mode === "bigint" ? new SingleStoreDecimalBigIntBuilder(name, config) : new SingleStoreDecimalBuilder(name, config);
}

//#endregion
exports.SingleStoreDecimal = SingleStoreDecimal;
exports.SingleStoreDecimalBigInt = SingleStoreDecimalBigInt;
exports.SingleStoreDecimalBigIntBuilder = SingleStoreDecimalBigIntBuilder;
exports.SingleStoreDecimalBuilder = SingleStoreDecimalBuilder;
exports.SingleStoreDecimalNumber = SingleStoreDecimalNumber;
exports.SingleStoreDecimalNumberBuilder = SingleStoreDecimalNumberBuilder;
exports.decimal = decimal;
//# sourceMappingURL=decimal.cjs.map