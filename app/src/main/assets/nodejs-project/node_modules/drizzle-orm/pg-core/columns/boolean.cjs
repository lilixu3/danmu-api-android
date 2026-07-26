Object.defineProperty(exports, Symbol.toStringTag, { value: 'Module' });
const require_runtime = require('../../_virtual/_rolldown/runtime.cjs');
const require_pg_core_columns_common = require('./common.cjs');
let __entity_ts = require("../../entity.cjs");

//#region src/pg-core/columns/boolean.ts
var PgBooleanBuilder = class extends require_pg_core_columns_common.PgColumnBuilder {
	static [__entity_ts.entityKind] = "PgBooleanBuilder";
	constructor(name) {
		super(name, "boolean", "PgBoolean");
	}
	/** @internal */
	build(table) {
		return new PgBoolean(table, this.config);
	}
};
var PgBoolean = class extends require_pg_core_columns_common.PgColumn {
	static [__entity_ts.entityKind] = "PgBoolean";
	/** @internal */
	codec = "bool";
	getSQLType() {
		return "boolean";
	}
};
function boolean(name) {
	return new PgBooleanBuilder(name ?? "");
}

//#endregion
exports.PgBoolean = PgBoolean;
exports.PgBooleanBuilder = PgBooleanBuilder;
exports.boolean = boolean;
//# sourceMappingURL=boolean.cjs.map