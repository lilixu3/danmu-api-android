Object.defineProperty(exports, Symbol.toStringTag, { value: 'Module' });
const require_runtime = require('../../_virtual/_rolldown/runtime.cjs');
const require_pg_core_columns_common = require('./common.cjs');
let __entity_ts = require("../../entity.cjs");
let __sql_sql_ts = require("../../sql/sql.cjs");

//#region src/pg-core/columns/uuid.ts
var PgUUIDBuilder = class extends require_pg_core_columns_common.PgColumnBuilder {
	static [__entity_ts.entityKind] = "PgUUIDBuilder";
	constructor(name) {
		super(name, "string uuid", "PgUUID");
	}
	/**
	* Adds `default gen_random_uuid()` to the column definition.
	*/
	defaultRandom() {
		return this.default(__sql_sql_ts.sql`gen_random_uuid()`);
	}
	/** @internal */
	build(table) {
		return new PgUUID(table, this.config);
	}
};
var PgUUID = class extends require_pg_core_columns_common.PgColumn {
	static [__entity_ts.entityKind] = "PgUUID";
	/** @internal */
	codec = "uuid";
	getSQLType() {
		return "uuid";
	}
};
function uuid(name) {
	return new PgUUIDBuilder(name ?? "");
}

//#endregion
exports.PgUUID = PgUUID;
exports.PgUUIDBuilder = PgUUIDBuilder;
exports.uuid = uuid;
//# sourceMappingURL=uuid.cjs.map