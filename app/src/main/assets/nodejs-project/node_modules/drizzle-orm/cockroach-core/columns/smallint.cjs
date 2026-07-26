Object.defineProperty(exports, Symbol.toStringTag, { value: 'Module' });
const require_runtime = require('../../_virtual/_rolldown/runtime.cjs');
const require_cockroach_core_columns_common = require('./common.cjs');
const require_cockroach_core_columns_int_common = require('./int.common.cjs');
let __entity_ts = require("../../entity.cjs");

//#region src/cockroach-core/columns/smallint.ts
var CockroachSmallIntBuilder = class extends require_cockroach_core_columns_int_common.CockroachIntColumnBaseBuilder {
	static [__entity_ts.entityKind] = "CockroachSmallIntBuilder";
	constructor(name) {
		super(name, "number int16", "CockroachSmallInt");
	}
	/** @internal */
	build(table) {
		return new CockroachSmallInt(table, this.config);
	}
};
var CockroachSmallInt = class extends require_cockroach_core_columns_common.CockroachColumn {
	static [__entity_ts.entityKind] = "CockroachSmallInt";
	getSQLType() {
		return "int2";
	}
	mapFromDriverValue = (value) => {
		if (typeof value === "string") return Number(value);
		return value;
	};
};
function smallint(name) {
	return new CockroachSmallIntBuilder(name ?? "");
}
function int2(name) {
	return new CockroachSmallIntBuilder(name ?? "");
}

//#endregion
exports.CockroachSmallInt = CockroachSmallInt;
exports.CockroachSmallIntBuilder = CockroachSmallIntBuilder;
exports.int2 = int2;
exports.smallint = smallint;
//# sourceMappingURL=smallint.cjs.map