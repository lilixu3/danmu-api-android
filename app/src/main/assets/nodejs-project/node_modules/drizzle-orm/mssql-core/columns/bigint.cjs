Object.defineProperty(exports, Symbol.toStringTag, { value: 'Module' });
const require_runtime = require('../../_virtual/_rolldown/runtime.cjs');
const require_mssql_core_columns_common = require('./common.cjs');
let __entity_ts = require("../../entity.cjs");
let __utils_ts = require("../../utils.cjs");

//#region src/mssql-core/columns/bigint.ts
var MsSqlBigIntBuilder = class extends require_mssql_core_columns_common.MsSqlColumnBuilderWithIdentity {
	static [__entity_ts.entityKind] = "MsSqlBigIntBuilder";
	constructor(name, config) {
		const { mode } = config;
		super(name, mode === "string" ? "string int64" : mode === "number" ? "number int53" : "bigint int64", mode === "string" ? "MsSqlBigIntString" : mode === "number" ? "MsSqlBigIntNumber" : "MsSqlBigInt");
		this.config.mode = mode;
	}
	/** @internal */
	build(table) {
		return new MsSqlBigInt(table, this.config);
	}
};
var MsSqlBigInt = class extends require_mssql_core_columns_common.MsSqlColumnWithIdentity {
	static [__entity_ts.entityKind] = "MsSqlBigInt";
	mode = this.config.mode;
	getSQLType() {
		return `bigint`;
	}
	constructor(table, config) {
		super(table, config);
		this.mode = config.mode;
	}
	mapFromDriverValue = (value) => {
		return this.mode === "string" ? value.toString() : this.mode === "number" ? Number(value) : BigInt(value);
	};
};
function bigint(a, b) {
	const { name, config } = (0, __utils_ts.getColumnNameAndConfig)(a, b);
	return new MsSqlBigIntBuilder(name, config);
}

//#endregion
exports.MsSqlBigInt = MsSqlBigInt;
exports.MsSqlBigIntBuilder = MsSqlBigIntBuilder;
exports.bigint = bigint;
//# sourceMappingURL=bigint.cjs.map