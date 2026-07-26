Object.defineProperty(exports, Symbol.toStringTag, { value: 'Module' });
const require_runtime = require('../../_virtual/_rolldown/runtime.cjs');
const require_singlestore_core_columns_common = require('./common.cjs');
let __entity_ts = require("../../entity.cjs");

//#region src/singlestore-core/columns/year.ts
var SingleStoreYearBuilder = class extends require_singlestore_core_columns_common.SingleStoreColumnBuilder {
	static [__entity_ts.entityKind] = "SingleStoreYearBuilder";
	constructor(name) {
		super(name, "number year", "SingleStoreYear");
	}
	/** @internal */
	build(table) {
		return new SingleStoreYear(table, this.config);
	}
};
var SingleStoreYear = class extends require_singlestore_core_columns_common.SingleStoreColumn {
	static [__entity_ts.entityKind] = "SingleStoreYear";
	getSQLType() {
		return `year`;
	}
	mapFromDriverValue = (value) => {
		if (typeof value !== "number") return Number(value);
		return value;
	};
};
function year(name) {
	return new SingleStoreYearBuilder(name ?? "");
}

//#endregion
exports.SingleStoreYear = SingleStoreYear;
exports.SingleStoreYearBuilder = SingleStoreYearBuilder;
exports.year = year;
//# sourceMappingURL=year.cjs.map