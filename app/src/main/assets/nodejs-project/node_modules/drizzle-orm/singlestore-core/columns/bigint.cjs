Object.defineProperty(exports, Symbol.toStringTag, { value: 'Module' });
const require_runtime = require('../../_virtual/_rolldown/runtime.cjs');
const require_singlestore_core_columns_common = require('./common.cjs');
let __entity_ts = require("../../entity.cjs");
let __utils_ts = require("../../utils.cjs");

//#region src/singlestore-core/columns/bigint.ts
var SingleStoreBigInt53Builder = class extends require_singlestore_core_columns_common.SingleStoreColumnBuilderWithAutoIncrement {
	static [__entity_ts.entityKind] = "SingleStoreBigInt53Builder";
	constructor(name, unsigned = false) {
		super(name, unsigned ? "number uint53" : "number int53", "SingleStoreBigInt53");
		this.config.unsigned = unsigned;
	}
	/** @internal */
	build(table) {
		return new SingleStoreBigInt53(table, this.config);
	}
};
var SingleStoreBigInt53 = class extends require_singlestore_core_columns_common.SingleStoreColumnWithAutoIncrement {
	static [__entity_ts.entityKind] = "SingleStoreBigInt53";
	getSQLType() {
		return `bigint${this.config.unsigned ? " unsigned" : ""}`;
	}
	mapFromDriverValue = (value) => {
		if (typeof value === "number") return value;
		return Number(value);
	};
};
var SingleStoreBigInt64Builder = class extends require_singlestore_core_columns_common.SingleStoreColumnBuilderWithAutoIncrement {
	static [__entity_ts.entityKind] = "SingleStoreBigInt64Builder";
	constructor(name, unsigned = false) {
		super(name, unsigned ? "bigint uint64" : "bigint int64", "SingleStoreBigInt64");
		this.config.unsigned = unsigned;
	}
	/** @internal */
	build(table) {
		return new SingleStoreBigInt64(table, this.config);
	}
};
var SingleStoreBigInt64 = class extends require_singlestore_core_columns_common.SingleStoreColumnWithAutoIncrement {
	static [__entity_ts.entityKind] = "SingleStoreBigInt64";
	getSQLType() {
		return `bigint${this.config.unsigned ? " unsigned" : ""}`;
	}
	mapFromDriverValue = (value) => {
		return BigInt(value);
	};
};
var SingleStoreBigIntStringBuilder = class extends require_singlestore_core_columns_common.SingleStoreColumnBuilderWithAutoIncrement {
	static [__entity_ts.entityKind] = "SingleStoreBigIntStringBuilder";
	constructor(name, unsigned = false) {
		super(name, unsigned ? "string uint64" : "string int64", "SingleStoreBigIntString");
		this.config.unsigned = unsigned;
	}
	/** @internal */
	build(table) {
		return new SingleStoreBigIntString(table, this.config);
	}
};
var SingleStoreBigIntString = class extends require_singlestore_core_columns_common.SingleStoreColumnWithAutoIncrement {
	static [__entity_ts.entityKind] = "SingleStoreBigIntString";
	getSQLType() {
		return `bigint${this.config.unsigned ? " unsigned" : ""}`;
	}
};
function bigint(a, b) {
	const { name, config } = (0, __utils_ts.getColumnNameAndConfig)(a, b);
	if (config.mode === "number") return new SingleStoreBigInt53Builder(name, config.unsigned);
	if (config.mode === "string") return new SingleStoreBigIntStringBuilder(name, config.unsigned);
	return new SingleStoreBigInt64Builder(name, config.unsigned);
}

//#endregion
exports.SingleStoreBigInt53 = SingleStoreBigInt53;
exports.SingleStoreBigInt53Builder = SingleStoreBigInt53Builder;
exports.SingleStoreBigInt64 = SingleStoreBigInt64;
exports.SingleStoreBigInt64Builder = SingleStoreBigInt64Builder;
exports.SingleStoreBigIntString = SingleStoreBigIntString;
exports.SingleStoreBigIntStringBuilder = SingleStoreBigIntStringBuilder;
exports.bigint = bigint;
//# sourceMappingURL=bigint.cjs.map