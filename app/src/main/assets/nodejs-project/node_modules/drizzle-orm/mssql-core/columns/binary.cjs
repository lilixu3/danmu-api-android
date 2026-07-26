Object.defineProperty(exports, Symbol.toStringTag, { value: 'Module' });
const require_runtime = require('../../_virtual/_rolldown/runtime.cjs');
const require_mssql_core_columns_common = require('./common.cjs');
let __entity_ts = require("../../entity.cjs");
let __utils_ts = require("../../utils.cjs");

//#region src/mssql-core/columns/binary.ts
var MsSqlBinaryBuilder = class extends require_mssql_core_columns_common.MsSqlColumnBuilder {
	static [__entity_ts.entityKind] = "MsSqlBinaryBuilder";
	constructor(name, length) {
		super(name, "object buffer", "MsSqlBinary");
		this.config.length = length ?? 1;
		this.config.setLength = length !== void 0;
	}
	/** @internal */
	build(table) {
		return new MsSqlBinary(table, this.config);
	}
};
var MsSqlBinary = class extends require_mssql_core_columns_common.MsSqlColumn {
	static [__entity_ts.entityKind] = "MsSqlBinary";
	getSQLType() {
		return this.config.setLength ? `binary(${this.length})` : `binary`;
	}
};
function binary(a, b = {}) {
	const { name, config } = (0, __utils_ts.getColumnNameAndConfig)(a, b);
	return new MsSqlBinaryBuilder(name, config.length);
}

//#endregion
exports.MsSqlBinary = MsSqlBinary;
exports.MsSqlBinaryBuilder = MsSqlBinaryBuilder;
exports.binary = binary;
//# sourceMappingURL=binary.cjs.map