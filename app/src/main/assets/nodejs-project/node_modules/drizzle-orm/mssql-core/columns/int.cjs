Object.defineProperty(exports, Symbol.toStringTag, { value: 'Module' });
const require_runtime = require('../../_virtual/_rolldown/runtime.cjs');
const require_mssql_core_columns_common = require('./common.cjs');
let __entity_ts = require("../../entity.cjs");

//#region src/mssql-core/columns/int.ts
var MsSqlIntBuilder = class extends require_mssql_core_columns_common.MsSqlColumnBuilderWithIdentity {
	static [__entity_ts.entityKind] = "MsSqlIntBuilder";
	constructor(name) {
		super(name, "number int32", "MsSqlInt");
	}
	/** @internal */
	build(table) {
		return new MsSqlInt(table, this.config);
	}
};
var MsSqlInt = class extends require_mssql_core_columns_common.MsSqlColumnWithIdentity {
	static [__entity_ts.entityKind] = "MsSqlInt";
	getSQLType() {
		return `int`;
	}
};
function int(name) {
	return new MsSqlIntBuilder(name ?? "");
}

//#endregion
exports.MsSqlInt = MsSqlInt;
exports.MsSqlIntBuilder = MsSqlIntBuilder;
exports.int = int;
//# sourceMappingURL=int.cjs.map