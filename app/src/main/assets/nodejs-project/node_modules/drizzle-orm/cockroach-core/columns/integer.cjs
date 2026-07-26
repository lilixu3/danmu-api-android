Object.defineProperty(exports, Symbol.toStringTag, { value: 'Module' });
const require_runtime = require('../../_virtual/_rolldown/runtime.cjs');
const require_cockroach_core_columns_common = require('./common.cjs');
const require_cockroach_core_columns_int_common = require('./int.common.cjs');
let __entity_ts = require("../../entity.cjs");

//#region src/cockroach-core/columns/integer.ts
var CockroachIntegerBuilder = class extends require_cockroach_core_columns_int_common.CockroachIntColumnBaseBuilder {
	static [__entity_ts.entityKind] = "CockroachIntegerBuilder";
	constructor(name) {
		super(name, "number int32", "CockroachInteger");
	}
	/** @internal */
	build(table) {
		return new CockroachInteger(table, this.config);
	}
};
var CockroachInteger = class extends require_cockroach_core_columns_common.CockroachColumn {
	static [__entity_ts.entityKind] = "CockroachInteger";
	getSQLType() {
		return "int4";
	}
	mapFromDriverValue = (value) => {
		if (typeof value === "string") return Number.parseInt(value);
		return value;
	};
};
function int4(name) {
	return new CockroachIntegerBuilder(name ?? "");
}

//#endregion
exports.CockroachInteger = CockroachInteger;
exports.CockroachIntegerBuilder = CockroachIntegerBuilder;
exports.int4 = int4;
//# sourceMappingURL=integer.cjs.map