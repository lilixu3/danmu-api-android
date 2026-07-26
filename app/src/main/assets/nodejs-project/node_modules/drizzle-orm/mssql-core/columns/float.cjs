Object.defineProperty(exports, Symbol.toStringTag, { value: 'Module' });
const require_runtime = require('../../_virtual/_rolldown/runtime.cjs');
const require_mssql_core_columns_common = require('./common.cjs');
let __entity_ts = require("../../entity.cjs");
let __utils_ts = require("../../utils.cjs");

//#region src/mssql-core/columns/float.ts
var MsSqlFloatBuilder = class extends require_mssql_core_columns_common.MsSqlColumnBuilderWithIdentity {
	static [__entity_ts.entityKind] = "MsSqlFloatBuilder";
	constructor(name, config) {
		super(name, "number double", "MsSqlFloat");
		this.config.precision = config?.precision;
	}
	/** @internal */
	build(table) {
		return new MsSqlFloat(table, this.config);
	}
};
var MsSqlFloat = class extends require_mssql_core_columns_common.MsSqlColumnWithIdentity {
	static [__entity_ts.entityKind] = "MsSqlFloat";
	precision = this.config.precision;
	getSQLType() {
		return `float${this.precision === void 0 ? "" : `(${this.precision})`}`;
	}
};
function float(a, b = {}) {
	const { name, config } = (0, __utils_ts.getColumnNameAndConfig)(a, b);
	return new MsSqlFloatBuilder(name, config);
}

//#endregion
exports.MsSqlFloat = MsSqlFloat;
exports.MsSqlFloatBuilder = MsSqlFloatBuilder;
exports.float = float;
//# sourceMappingURL=float.cjs.map