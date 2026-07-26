Object.defineProperty(exports, Symbol.toStringTag, { value: 'Module' });
const require_runtime = require('../../_virtual/_rolldown/runtime.cjs');
const require_cockroach_core_columns_common = require('./common.cjs');
const require_cockroach_core_columns_int_common = require('./int.common.cjs');
let __entity_ts = require("../../entity.cjs");
let __utils_ts = require("../../utils.cjs");

//#region src/cockroach-core/columns/bigint.ts
var CockroachBigInt53Builder = class extends require_cockroach_core_columns_int_common.CockroachIntColumnBaseBuilder {
	static [__entity_ts.entityKind] = "CockroachBigInt53Builder";
	constructor(name) {
		super(name, "number int53", "CockroachBigInt53");
	}
	/** @internal */
	build(table) {
		return new CockroachBigInt53(table, this.config);
	}
};
var CockroachBigInt53 = class extends require_cockroach_core_columns_common.CockroachColumn {
	static [__entity_ts.entityKind] = "CockroachBigInt53";
	getSQLType() {
		return "int8";
	}
	mapFromDriverValue = (value) => {
		if (typeof value === "number") return value;
		return Number(value);
	};
};
var CockroachBigInt64Builder = class extends require_cockroach_core_columns_int_common.CockroachIntColumnBaseBuilder {
	static [__entity_ts.entityKind] = "CockroachBigInt64Builder";
	constructor(name) {
		super(name, "bigint int64", "CockroachBigInt64");
	}
	/** @internal */
	build(table) {
		return new CockroachBigInt64(table, this.config);
	}
};
var CockroachBigInt64 = class extends require_cockroach_core_columns_common.CockroachColumn {
	static [__entity_ts.entityKind] = "CockroachBigInt64";
	getSQLType() {
		return "int8";
	}
	mapFromDriverValue = (value) => {
		return BigInt(value);
	};
};
function bigint(a, b) {
	const { name, config } = (0, __utils_ts.getColumnNameAndConfig)(a, b);
	if (config.mode === "number") return new CockroachBigInt53Builder(name);
	return new CockroachBigInt64Builder(name);
}
function int8(a, b) {
	const { name, config } = (0, __utils_ts.getColumnNameAndConfig)(a, b);
	if (config.mode === "number") return new CockroachBigInt53Builder(name);
	return new CockroachBigInt64Builder(name);
}

//#endregion
exports.CockroachBigInt53 = CockroachBigInt53;
exports.CockroachBigInt53Builder = CockroachBigInt53Builder;
exports.CockroachBigInt64 = CockroachBigInt64;
exports.CockroachBigInt64Builder = CockroachBigInt64Builder;
exports.bigint = bigint;
exports.int8 = int8;
//# sourceMappingURL=bigint.cjs.map