Object.defineProperty(exports, Symbol.toStringTag, { value: 'Module' });
const require_runtime = require('../../_virtual/_rolldown/runtime.cjs');
const require_cockroach_core_columns_common = require('./common.cjs');
let __entity_ts = require("../../entity.cjs");

//#region src/cockroach-core/columns/inet.ts
var CockroachInetBuilder = class extends require_cockroach_core_columns_common.CockroachColumnWithArrayBuilder {
	static [__entity_ts.entityKind] = "CockroachInetBuilder";
	constructor(name) {
		super(name, "string inet", "CockroachInet");
	}
	/** @internal */
	build(table) {
		return new CockroachInet(table, this.config);
	}
};
var CockroachInet = class extends require_cockroach_core_columns_common.CockroachColumn {
	static [__entity_ts.entityKind] = "CockroachInet";
	getSQLType() {
		return "inet";
	}
};
function inet(name) {
	return new CockroachInetBuilder(name ?? "");
}

//#endregion
exports.CockroachInet = CockroachInet;
exports.CockroachInetBuilder = CockroachInetBuilder;
exports.inet = inet;
//# sourceMappingURL=inet.cjs.map