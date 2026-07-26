Object.defineProperty(exports, Symbol.toStringTag, { value: 'Module' });
const require_runtime = require('../../_virtual/_rolldown/runtime.cjs');
const require_pg_core_columns_common = require('./common.cjs');
const require_pg_core_columns_int_common = require('./int.common.cjs');
let __entity_ts = require("../../entity.cjs");

//#region src/pg-core/columns/smallint.ts
var PgSmallIntBuilder = class extends require_pg_core_columns_int_common.PgIntColumnBuilder {
	static [__entity_ts.entityKind] = "PgSmallIntBuilder";
	constructor(name) {
		super(name, "number int16", "PgSmallInt");
	}
	/** @internal */
	build(table) {
		return new PgSmallInt(table, this.config);
	}
};
var PgSmallInt = class extends require_pg_core_columns_common.PgColumn {
	static [__entity_ts.entityKind] = "PgSmallInt";
	/** @internal */
	codec = "smallint";
	getSQLType() {
		return "smallint";
	}
};
function smallint(name) {
	return new PgSmallIntBuilder(name ?? "");
}

//#endregion
exports.PgSmallInt = PgSmallInt;
exports.PgSmallIntBuilder = PgSmallIntBuilder;
exports.smallint = smallint;
//# sourceMappingURL=smallint.cjs.map