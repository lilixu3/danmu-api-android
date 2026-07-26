Object.defineProperty(exports, Symbol.toStringTag, { value: 'Module' });
const require_runtime = require('../../_virtual/_rolldown/runtime.cjs');
const require_sqlite_core_columns_common = require('./common.cjs');
let __entity_ts = require("../../entity.cjs");
let __utils_ts = require("../../utils.cjs");

//#region src/sqlite-core/columns/numeric.ts
var SQLiteNumericBuilder = class extends require_sqlite_core_columns_common.SQLiteColumnBuilder {
	static [__entity_ts.entityKind] = "SQLiteNumericBuilder";
	constructor(name) {
		super(name, "string numeric", "SQLiteNumeric");
	}
	/** @internal */
	build(table) {
		return new SQLiteNumeric(table, this.config);
	}
};
var SQLiteNumeric = class extends require_sqlite_core_columns_common.SQLiteColumn {
	static [__entity_ts.entityKind] = "SQLiteNumeric";
	mapFromDriverValue = (value) => {
		if (typeof value === "string") return value;
		return String(value);
	};
	getSQLType() {
		return "numeric";
	}
};
var SQLiteNumericNumberBuilder = class extends require_sqlite_core_columns_common.SQLiteColumnBuilder {
	static [__entity_ts.entityKind] = "SQLiteNumericNumberBuilder";
	constructor(name) {
		super(name, "number", "SQLiteNumericNumber");
	}
	/** @internal */
	build(table) {
		return new SQLiteNumericNumber(table, this.config);
	}
};
var SQLiteNumericNumber = class extends require_sqlite_core_columns_common.SQLiteColumn {
	static [__entity_ts.entityKind] = "SQLiteNumericNumber";
	mapFromDriverValue = (value) => {
		if (typeof value === "number") return value;
		return Number(value);
	};
	mapToDriverValue = String;
	getSQLType() {
		return "numeric";
	}
};
var SQLiteNumericBigIntBuilder = class extends require_sqlite_core_columns_common.SQLiteColumnBuilder {
	static [__entity_ts.entityKind] = "SQLiteNumericBigIntBuilder";
	constructor(name) {
		super(name, "bigint int64", "SQLiteNumericBigInt");
	}
	/** @internal */
	build(table) {
		return new SQLiteNumericBigInt(table, this.config);
	}
};
var SQLiteNumericBigInt = class extends require_sqlite_core_columns_common.SQLiteColumn {
	static [__entity_ts.entityKind] = "SQLiteNumericBigInt";
	mapFromDriverValue = BigInt;
	mapToDriverValue = String;
	getSQLType() {
		return "numeric";
	}
};
function numeric(a, b) {
	const { name, config } = (0, __utils_ts.getColumnNameAndConfig)(a, b);
	const mode = config?.mode;
	return mode === "number" ? new SQLiteNumericNumberBuilder(name) : mode === "bigint" ? new SQLiteNumericBigIntBuilder(name) : new SQLiteNumericBuilder(name);
}

//#endregion
exports.SQLiteNumeric = SQLiteNumeric;
exports.SQLiteNumericBigInt = SQLiteNumericBigInt;
exports.SQLiteNumericBigIntBuilder = SQLiteNumericBigIntBuilder;
exports.SQLiteNumericBuilder = SQLiteNumericBuilder;
exports.SQLiteNumericNumber = SQLiteNumericNumber;
exports.SQLiteNumericNumberBuilder = SQLiteNumericNumberBuilder;
exports.numeric = numeric;
//# sourceMappingURL=numeric.cjs.map