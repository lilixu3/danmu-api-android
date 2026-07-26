Object.defineProperty(exports, Symbol.toStringTag, { value: 'Module' });
const require_runtime = require('../../_virtual/_rolldown/runtime.cjs');
const require_pg_core_columns_common = require('./common.cjs');
let __entity_ts = require("../../entity.cjs");

//#region src/pg-core/columns/cidr.ts
var PgCidrBuilder = class extends require_pg_core_columns_common.PgColumnBuilder {
	static [__entity_ts.entityKind] = "PgCidrBuilder";
	constructor(name) {
		super(name, "string cidr", "PgCidr");
	}
	/** @internal */
	build(table) {
		return new PgCidr(table, this.config);
	}
};
var PgCidr = class extends require_pg_core_columns_common.PgColumn {
	static [__entity_ts.entityKind] = "PgCidr";
	/** @internal */
	codec = "cidr";
	getSQLType() {
		return "cidr";
	}
};
function cidr(name) {
	return new PgCidrBuilder(name ?? "");
}

//#endregion
exports.PgCidr = PgCidr;
exports.PgCidrBuilder = PgCidrBuilder;
exports.cidr = cidr;
//# sourceMappingURL=cidr.cjs.map