Object.defineProperty(exports, Symbol.toStringTag, { value: 'Module' });
const require_runtime = require('../../_virtual/_rolldown/runtime.cjs');
const require_mssql_core_columns_common = require('./common.cjs');
let __entity_ts = require("../../entity.cjs");
let __utils_ts = require("../../utils.cjs");

//#region src/mssql-core/columns/varbinary.ts
var MsSqlVarBinaryBuilder = class extends require_mssql_core_columns_common.MsSqlColumnBuilder {
	static [__entity_ts.entityKind] = "MsSqlVarBinaryBuilder";
	/** @internal */
	constructor(name, config) {
		super(name, "object buffer", "MsSqlVarBinary");
		this.config.length = typeof config?.length === "number" ? config.length : config?.length === "max" ? 2147483647 : 1;
		this.config.rawLength = config?.length;
	}
	/** @internal */
	build(table) {
		return new MsSqlVarBinary(table, this.config);
	}
};
var MsSqlVarBinary = class extends require_mssql_core_columns_common.MsSqlColumn {
	static [__entity_ts.entityKind] = "MsSqlVarBinary";
	getSQLType() {
		return this.config.rawLength === void 0 ? `varbinary` : `varbinary(${this.config.rawLength})`;
	}
};
function varbinary(a, b) {
	const { name, config } = (0, __utils_ts.getColumnNameAndConfig)(a, b);
	return new MsSqlVarBinaryBuilder(name, config);
}

//#endregion
exports.MsSqlVarBinary = MsSqlVarBinary;
exports.MsSqlVarBinaryBuilder = MsSqlVarBinaryBuilder;
exports.varbinary = varbinary;
//# sourceMappingURL=varbinary.cjs.map