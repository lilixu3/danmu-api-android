Object.defineProperty(exports, Symbol.toStringTag, { value: 'Module' });
const require_runtime = require('../../_virtual/_rolldown/runtime.cjs');
const require_pg_core_columns_common = require('./common.cjs');
let __entity_ts = require("../../entity.cjs");

//#region src/pg-core/columns/macaddr.ts
var PgMacaddrBuilder = class extends require_pg_core_columns_common.PgColumnBuilder {
	static [__entity_ts.entityKind] = "PgMacaddrBuilder";
	constructor(name) {
		super(name, "string macaddr", "PgMacaddr");
	}
	/** @internal */
	build(table) {
		return new PgMacaddr(table, this.config);
	}
};
var PgMacaddr = class extends require_pg_core_columns_common.PgColumn {
	static [__entity_ts.entityKind] = "PgMacaddr";
	/** @internal */
	codec = "macaddr";
	getSQLType() {
		return "macaddr";
	}
};
function macaddr(name) {
	return new PgMacaddrBuilder(name ?? "");
}

//#endregion
exports.PgMacaddr = PgMacaddr;
exports.PgMacaddrBuilder = PgMacaddrBuilder;
exports.macaddr = macaddr;
//# sourceMappingURL=macaddr.cjs.map