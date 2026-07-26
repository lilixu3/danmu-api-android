Object.defineProperty(exports, Symbol.toStringTag, { value: 'Module' });
const require_runtime = require('../../_virtual/_rolldown/runtime.cjs');
const require_cockroach_core_columns_common = require('./common.cjs');
let __entity_ts = require("../../entity.cjs");
let __utils_ts = require("../../utils.cjs");

//#region src/cockroach-core/columns/bit.ts
var CockroachBitBuilder = class extends require_cockroach_core_columns_common.CockroachColumnWithArrayBuilder {
	static [__entity_ts.entityKind] = "CockroachBitBuilder";
	constructor(name, config) {
		super(name, "string binary", "CockroachBit");
		this.config.length = config.length ?? 1;
		this.config.setLength = config.length !== void 0;
		this.config.isLengthExact = true;
	}
	/** @internal */
	build(table) {
		return new CockroachBit(table, this.config);
	}
};
var CockroachBit = class extends require_cockroach_core_columns_common.CockroachColumn {
	static [__entity_ts.entityKind] = "CockroachBit";
	getSQLType() {
		return this.config.setLength ? `bit(${this.length})` : "bit";
	}
};
function bit(a, b = {}) {
	const { name, config } = (0, __utils_ts.getColumnNameAndConfig)(a, b);
	return new CockroachBitBuilder(name, config);
}

//#endregion
exports.CockroachBit = CockroachBit;
exports.CockroachBitBuilder = CockroachBitBuilder;
exports.bit = bit;
//# sourceMappingURL=bit.cjs.map