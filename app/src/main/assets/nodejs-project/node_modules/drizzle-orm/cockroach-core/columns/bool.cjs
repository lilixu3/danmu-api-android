Object.defineProperty(exports, Symbol.toStringTag, { value: 'Module' });
const require_runtime = require('../../_virtual/_rolldown/runtime.cjs');
const require_cockroach_core_columns_common = require('./common.cjs');
let __entity_ts = require("../../entity.cjs");

//#region src/cockroach-core/columns/bool.ts
var CockroachBooleanBuilder = class extends require_cockroach_core_columns_common.CockroachColumnWithArrayBuilder {
	static [__entity_ts.entityKind] = "CockroachBooleanBuilder";
	constructor(name) {
		super(name, "boolean", "CockroachBoolean");
	}
	/** @internal */
	build(table) {
		return new CockroachBoolean(table, this.config);
	}
};
var CockroachBoolean = class extends require_cockroach_core_columns_common.CockroachColumn {
	static [__entity_ts.entityKind] = "CockroachBoolean";
	getSQLType() {
		return "bool";
	}
};
function bool(name) {
	return new CockroachBooleanBuilder(name ?? "");
}
const boolean = bool;

//#endregion
exports.CockroachBoolean = CockroachBoolean;
exports.CockroachBooleanBuilder = CockroachBooleanBuilder;
exports.bool = bool;
exports.boolean = boolean;
//# sourceMappingURL=bool.cjs.map