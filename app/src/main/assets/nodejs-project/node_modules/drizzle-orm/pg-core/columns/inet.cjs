Object.defineProperty(exports, Symbol.toStringTag, { value: 'Module' });
const require_runtime = require('../../_virtual/_rolldown/runtime.cjs');
const require_pg_core_columns_common = require('./common.cjs');
let __entity_ts = require("../../entity.cjs");

//#region src/pg-core/columns/inet.ts
var PgInetBuilder = class extends require_pg_core_columns_common.PgColumnBuilder {
	static [__entity_ts.entityKind] = "PgInetBuilder";
	constructor(name) {
		super(name, "string inet", "PgInet");
	}
	/** @internal */
	build(table) {
		return new PgInet(table, this.config);
	}
};
var PgInet = class extends require_pg_core_columns_common.PgColumn {
	static [__entity_ts.entityKind] = "PgInet";
	/** @internal */
	codec = "inet";
	getSQLType() {
		return "inet";
	}
};
function inet(name) {
	return new PgInetBuilder(name ?? "");
}

//#endregion
exports.PgInet = PgInet;
exports.PgInetBuilder = PgInetBuilder;
exports.inet = inet;
//# sourceMappingURL=inet.cjs.map