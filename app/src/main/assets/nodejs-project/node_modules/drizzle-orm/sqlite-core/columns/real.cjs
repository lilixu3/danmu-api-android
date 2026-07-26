Object.defineProperty(exports, Symbol.toStringTag, { value: 'Module' });
const require_runtime = require('../../_virtual/_rolldown/runtime.cjs');
const require_sqlite_core_columns_common = require('./common.cjs');
let __entity_ts = require("../../entity.cjs");

//#region src/sqlite-core/columns/real.ts
var SQLiteRealBuilder = class extends require_sqlite_core_columns_common.SQLiteColumnBuilder {
	static [__entity_ts.entityKind] = "SQLiteRealBuilder";
	constructor(name) {
		super(name, "number double", "SQLiteReal");
	}
	/** @internal */
	build(table) {
		return new SQLiteReal(table, this.config);
	}
};
var SQLiteReal = class extends require_sqlite_core_columns_common.SQLiteColumn {
	static [__entity_ts.entityKind] = "SQLiteReal";
	getSQLType() {
		return "real";
	}
};
function real(name) {
	return new SQLiteRealBuilder(name ?? "");
}

//#endregion
exports.SQLiteReal = SQLiteReal;
exports.SQLiteRealBuilder = SQLiteRealBuilder;
exports.real = real;
//# sourceMappingURL=real.cjs.map