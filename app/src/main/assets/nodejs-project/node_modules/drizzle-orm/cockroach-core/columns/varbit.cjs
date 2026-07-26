Object.defineProperty(exports, Symbol.toStringTag, { value: 'Module' });
const require_runtime = require('../../_virtual/_rolldown/runtime.cjs');
const require_cockroach_core_columns_common = require('./common.cjs');
let __entity_ts = require("../../entity.cjs");
let __utils_ts = require("../../utils.cjs");

//#region src/cockroach-core/columns/varbit.ts
var CockroachVarbitBuilder = class extends require_cockroach_core_columns_common.CockroachColumnWithArrayBuilder {
	static [__entity_ts.entityKind] = "CockroachVarbitBuilder";
	constructor(name, config) {
		super(name, "string binary", "CockroachVarbit");
		this.config.length = config.length;
	}
	/** @internal */
	build(table) {
		return new CockroachVarbit(table, this.config);
	}
};
var CockroachVarbit = class extends require_cockroach_core_columns_common.CockroachColumn {
	static [__entity_ts.entityKind] = "CockroachVarbit";
	getSQLType() {
		return this.length ? `varbit(${this.length})` : "varbit";
	}
};
function varbit(a, b = {}) {
	const { name, config } = (0, __utils_ts.getColumnNameAndConfig)(a, b);
	return new CockroachVarbitBuilder(name, config);
}

//#endregion
exports.CockroachVarbit = CockroachVarbit;
exports.CockroachVarbitBuilder = CockroachVarbitBuilder;
exports.varbit = varbit;
//# sourceMappingURL=varbit.cjs.map