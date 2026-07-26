Object.defineProperty(exports, Symbol.toStringTag, { value: 'Module' });
const require_runtime = require('../../_virtual/_rolldown/runtime.cjs');
const require_cockroach_core_columns_common = require('./common.cjs');
let __entity_ts = require("../../entity.cjs");
let __sql_sql_ts = require("../../sql/sql.cjs");

//#region src/cockroach-core/columns/uuid.ts
var CockroachUUIDBuilder = class extends require_cockroach_core_columns_common.CockroachColumnWithArrayBuilder {
	static [__entity_ts.entityKind] = "CockroachUUIDBuilder";
	constructor(name) {
		super(name, "string uuid", "CockroachUUID");
	}
	/**
	* Adds `default gen_random_uuid()` to the column definition.
	*/
	defaultRandom() {
		return this.default(__sql_sql_ts.sql`gen_random_uuid()`);
	}
	/** @internal */
	build(table) {
		return new CockroachUUID(table, this.config);
	}
};
var CockroachUUID = class extends require_cockroach_core_columns_common.CockroachColumn {
	static [__entity_ts.entityKind] = "CockroachUUID";
	getSQLType() {
		return "uuid";
	}
};
function uuid(name) {
	return new CockroachUUIDBuilder(name ?? "");
}

//#endregion
exports.CockroachUUID = CockroachUUID;
exports.CockroachUUIDBuilder = CockroachUUIDBuilder;
exports.uuid = uuid;
//# sourceMappingURL=uuid.cjs.map