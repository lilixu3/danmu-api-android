Object.defineProperty(exports, Symbol.toStringTag, { value: 'Module' });
const require_runtime = require('../../_virtual/_rolldown/runtime.cjs');
const require_mssql_core_columns_common = require('./common.cjs');
let __entity_ts = require("../../entity.cjs");

//#region src/mssql-core/columns/bit.ts
var MsSqlBitBuilder = class extends require_mssql_core_columns_common.MsSqlColumnBuilderWithIdentity {
	static [__entity_ts.entityKind] = "MsSqlBitBuilder";
	constructor(name) {
		super(name, "boolean", "MsSqlBit");
	}
	/** @internal */
	build(table) {
		return new MsSqlBit(table, this.config);
	}
};
var MsSqlBit = class extends require_mssql_core_columns_common.MsSqlColumnWithIdentity {
	static [__entity_ts.entityKind] = "MsSqlBit";
	getSQLType() {
		return `bit`;
	}
	mapFromDriverValue = Boolean;
};
function bit(name) {
	return new MsSqlBitBuilder(name ?? "");
}

//#endregion
exports.MsSqlBit = MsSqlBit;
exports.MsSqlBitBuilder = MsSqlBitBuilder;
exports.bit = bit;
//# sourceMappingURL=bit.cjs.map