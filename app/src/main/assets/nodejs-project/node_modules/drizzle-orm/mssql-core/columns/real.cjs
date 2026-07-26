Object.defineProperty(exports, Symbol.toStringTag, { value: 'Module' });
const require_runtime = require('../../_virtual/_rolldown/runtime.cjs');
const require_mssql_core_columns_common = require('./common.cjs');
let __entity_ts = require("../../entity.cjs");

//#region src/mssql-core/columns/real.ts
var MsSqlRealBuilder = class extends require_mssql_core_columns_common.MsSqlColumnBuilderWithIdentity {
	static [__entity_ts.entityKind] = "MsSqlRealBuilder";
	constructor(name) {
		super(name, "number float", "MsSqlReal");
	}
	/** @internal */
	build(table) {
		return new MsSqlReal(table, this.config);
	}
};
var MsSqlReal = class extends require_mssql_core_columns_common.MsSqlColumnWithIdentity {
	static [__entity_ts.entityKind] = "MsSqlReal";
	getSQLType() {
		return "real";
	}
	mapFromDriverValue = (value) => {
		return parseFloat(value.toPrecision(7));
	};
};
function real(name) {
	return new MsSqlRealBuilder(name ?? "");
}

//#endregion
exports.MsSqlReal = MsSqlReal;
exports.MsSqlRealBuilder = MsSqlRealBuilder;
exports.real = real;
//# sourceMappingURL=real.cjs.map