Object.defineProperty(exports, Symbol.toStringTag, { value: 'Module' });
const require_runtime = require('../../_virtual/_rolldown/runtime.cjs');
const require_pg_core_columns_common = require('./common.cjs');
let __entity_ts = require("../../entity.cjs");

//#region src/pg-core/columns/jsonb.ts
var PgJsonbBuilder = class extends require_pg_core_columns_common.PgColumnBuilder {
	static [__entity_ts.entityKind] = "PgJsonbBuilder";
	constructor(name) {
		super(name, "object json", "PgJsonb");
	}
	/** @internal */
	build(table) {
		return new PgJsonb(table, this.config);
	}
};
var PgJsonb = class extends require_pg_core_columns_common.PgColumn {
	static [__entity_ts.entityKind] = "PgJsonb";
	/** @internal */
	codec = "jsonb";
	constructor(table, config) {
		super(table, config);
	}
	getSQLType() {
		return "jsonb";
	}
};
function jsonb(name) {
	return new PgJsonbBuilder(name ?? "");
}

//#endregion
exports.PgJsonb = PgJsonb;
exports.PgJsonbBuilder = PgJsonbBuilder;
exports.jsonb = jsonb;
//# sourceMappingURL=jsonb.cjs.map