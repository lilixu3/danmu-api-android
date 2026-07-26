Object.defineProperty(exports, Symbol.toStringTag, { value: 'Module' });
const require_runtime = require('../../_virtual/_rolldown/runtime.cjs');
const require_pg_core_columns_common = require('./common.cjs');
let __entity_ts = require("../../entity.cjs");

//#region src/pg-core/columns/macaddr8.ts
var PgMacaddr8Builder = class extends require_pg_core_columns_common.PgColumnBuilder {
	static [__entity_ts.entityKind] = "PgMacaddr8Builder";
	constructor(name) {
		super(name, "string macaddr8", "PgMacaddr8");
	}
	/** @internal */
	build(table) {
		return new PgMacaddr8(table, this.config);
	}
};
var PgMacaddr8 = class extends require_pg_core_columns_common.PgColumn {
	static [__entity_ts.entityKind] = "PgMacaddr8";
	/** @internal */
	codec = "macaddr8";
	getSQLType() {
		return "macaddr8";
	}
};
function macaddr8(name) {
	return new PgMacaddr8Builder(name ?? "");
}

//#endregion
exports.PgMacaddr8 = PgMacaddr8;
exports.PgMacaddr8Builder = PgMacaddr8Builder;
exports.macaddr8 = macaddr8;
//# sourceMappingURL=macaddr8.cjs.map