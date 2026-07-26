Object.defineProperty(exports, Symbol.toStringTag, { value: 'Module' });
const require_runtime = require('../../_virtual/_rolldown/runtime.cjs');
const require_cockroach_core_columns_common = require('./common.cjs');
let __entity_ts = require("../../entity.cjs");

//#region src/cockroach-core/columns/float.ts
var CockroachFloatBuilder = class extends require_cockroach_core_columns_common.CockroachColumnWithArrayBuilder {
	static [__entity_ts.entityKind] = "CockroachFloatBuilder";
	constructor(name) {
		super(name, "number double", "CockroachFloat");
	}
	/** @internal */
	build(table) {
		return new CockroachFloat(table, this.config);
	}
};
var CockroachFloat = class extends require_cockroach_core_columns_common.CockroachColumn {
	static [__entity_ts.entityKind] = "CockroachFloat";
	getSQLType() {
		return "float";
	}
	mapFromDriverValue = (value) => {
		if (typeof value === "string") return Number.parseFloat(value);
		return value;
	};
};
function float(name) {
	return new CockroachFloatBuilder(name ?? "");
}
const doublePrecision = float;

//#endregion
exports.CockroachFloat = CockroachFloat;
exports.CockroachFloatBuilder = CockroachFloatBuilder;
exports.doublePrecision = doublePrecision;
exports.float = float;
//# sourceMappingURL=float.cjs.map