Object.defineProperty(exports, Symbol.toStringTag, { value: 'Module' });
const require_runtime = require('../../_virtual/_rolldown/runtime.cjs');
const require_mssql_core_columns_common = require('./common.cjs');
let __entity_ts = require("../../entity.cjs");

//#region src/mssql-core/columns/smallint.ts
var MsSqlSmallIntBuilder = class extends require_mssql_core_columns_common.MsSqlColumnBuilderWithIdentity {
	static [__entity_ts.entityKind] = "MsSqlSmallIntBuilder";
	constructor(name) {
		super(name, "number int16", "MsSqlSmallInt");
	}
	/** @internal */
	build(table) {
		return new MsSqlSmallInt(table, this.config);
	}
};
var MsSqlSmallInt = class extends require_mssql_core_columns_common.MsSqlColumnWithIdentity {
	static [__entity_ts.entityKind] = "MsSqlSmallInt";
	getSQLType() {
		return `smallint`;
	}
	mapFromDriverValue = (value) => {
		if (typeof value === "string") return Number(value);
		return value;
	};
};
function smallint(name) {
	return new MsSqlSmallIntBuilder(name ?? "");
}

//#endregion
exports.MsSqlSmallInt = MsSqlSmallInt;
exports.MsSqlSmallIntBuilder = MsSqlSmallIntBuilder;
exports.smallint = smallint;
//# sourceMappingURL=smallint.cjs.map