Object.defineProperty(exports, Symbol.toStringTag, { value: 'Module' });
const require_runtime = require('../../_virtual/_rolldown/runtime.cjs');
const require_mssql_core_columns_common = require('./common.cjs');
let __entity_ts = require("../../entity.cjs");

//#region src/mssql-core/columns/tinyint.ts
var MsSqlTinyIntBuilder = class extends require_mssql_core_columns_common.MsSqlColumnBuilderWithIdentity {
	static [__entity_ts.entityKind] = "MsSqlTinyIntBuilder";
	constructor(name) {
		super(name, "number uint8", "MsSqlTinyInt");
	}
	/** @internal */
	build(table) {
		return new MsSqlTinyInt(table, this.config);
	}
};
var MsSqlTinyInt = class extends require_mssql_core_columns_common.MsSqlColumnWithIdentity {
	static [__entity_ts.entityKind] = "MsSqlTinyInt";
	getSQLType() {
		return `tinyint`;
	}
	mapFromDriverValue = (value) => {
		if (typeof value === "string") return Number(value);
		return value;
	};
};
function tinyint(name) {
	return new MsSqlTinyIntBuilder(name ?? "");
}

//#endregion
exports.MsSqlTinyInt = MsSqlTinyInt;
exports.MsSqlTinyIntBuilder = MsSqlTinyIntBuilder;
exports.tinyint = tinyint;
//# sourceMappingURL=tinyint.cjs.map