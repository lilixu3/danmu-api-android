Object.defineProperty(exports, Symbol.toStringTag, { value: 'Module' });
const require_runtime = require('../../_virtual/_rolldown/runtime.cjs');
const require_pg_core_columns_common = require('./common.cjs');
let __entity_ts = require("../../entity.cjs");

//#region src/pg-core/columns/json.ts
var PgJsonBuilder = class extends require_pg_core_columns_common.PgColumnBuilder {
	static [__entity_ts.entityKind] = "PgJsonBuilder";
	constructor(name) {
		super(name, "object json", "PgJson");
	}
	/** @internal */
	build(table) {
		return new PgJson(table, this.config);
	}
};
var PgJson = class extends require_pg_core_columns_common.PgColumn {
	static [__entity_ts.entityKind] = "PgJson";
	/** @internal */
	codec = "json";
	constructor(table, config) {
		super(table, config);
	}
	getSQLType() {
		return "json";
	}
};
function json(name) {
	return new PgJsonBuilder(name ?? "");
}

//#endregion
exports.PgJson = PgJson;
exports.PgJsonBuilder = PgJsonBuilder;
exports.json = json;
//# sourceMappingURL=json.cjs.map