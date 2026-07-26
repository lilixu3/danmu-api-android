Object.defineProperty(exports, Symbol.toStringTag, { value: 'Module' });
const require_runtime = require('../../_virtual/_rolldown/runtime.cjs');
const require_cockroach_core_columns_common = require('./common.cjs');
let __entity_ts = require("../../entity.cjs");

//#region src/cockroach-core/columns/real.ts
var CockroachRealBuilder = class extends require_cockroach_core_columns_common.CockroachColumnWithArrayBuilder {
	static [__entity_ts.entityKind] = "CockroachRealBuilder";
	constructor(name, length) {
		super(name, "number float", "CockroachReal");
		this.config.length = length;
	}
	/** @internal */
	build(table) {
		return new CockroachReal(table, this.config);
	}
};
var CockroachReal = class extends require_cockroach_core_columns_common.CockroachColumn {
	static [__entity_ts.entityKind] = "CockroachReal";
	constructor(table, config) {
		super(table, config);
	}
	getSQLType() {
		return "real";
	}
	mapFromDriverValue = (value) => {
		if (typeof value === "string") return Number.parseFloat(value);
		return value;
	};
};
function real(name) {
	return new CockroachRealBuilder(name ?? "");
}

//#endregion
exports.CockroachReal = CockroachReal;
exports.CockroachRealBuilder = CockroachRealBuilder;
exports.real = real;
//# sourceMappingURL=real.cjs.map