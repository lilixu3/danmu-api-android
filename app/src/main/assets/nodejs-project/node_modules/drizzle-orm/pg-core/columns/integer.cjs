Object.defineProperty(exports, Symbol.toStringTag, { value: 'Module' });
const require_runtime = require('../../_virtual/_rolldown/runtime.cjs');
const require_pg_core_columns_common = require('./common.cjs');
const require_pg_core_columns_int_common = require('./int.common.cjs');
let __entity_ts = require("../../entity.cjs");

//#region src/pg-core/columns/integer.ts
var PgIntegerBuilder = class extends require_pg_core_columns_int_common.PgIntColumnBuilder {
	static [__entity_ts.entityKind] = "PgIntegerBuilder";
	constructor(name) {
		super(name, "number int32", "PgInteger");
	}
	/** @internal */
	build(table) {
		return new PgInteger(table, this.config);
	}
};
var PgInteger = class extends require_pg_core_columns_common.PgColumn {
	static [__entity_ts.entityKind] = "PgInteger";
	/** @internal */
	codec = "int";
	getSQLType() {
		return "integer";
	}
};
function integer(name) {
	return new PgIntegerBuilder(name ?? "");
}

//#endregion
exports.PgInteger = PgInteger;
exports.PgIntegerBuilder = PgIntegerBuilder;
exports.integer = integer;
//# sourceMappingURL=integer.cjs.map