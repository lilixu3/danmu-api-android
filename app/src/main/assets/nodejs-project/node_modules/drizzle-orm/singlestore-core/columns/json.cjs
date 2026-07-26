Object.defineProperty(exports, Symbol.toStringTag, { value: 'Module' });
const require_runtime = require('../../_virtual/_rolldown/runtime.cjs');
const require_singlestore_core_columns_common = require('./common.cjs');
let __entity_ts = require("../../entity.cjs");

//#region src/singlestore-core/columns/json.ts
var SingleStoreJsonBuilder = class extends require_singlestore_core_columns_common.SingleStoreColumnBuilder {
	static [__entity_ts.entityKind] = "SingleStoreJsonBuilder";
	constructor(name) {
		super(name, "object json", "SingleStoreJson");
	}
	/** @internal */
	build(table) {
		return new SingleStoreJson(table, this.config);
	}
};
var SingleStoreJson = class extends require_singlestore_core_columns_common.SingleStoreColumn {
	static [__entity_ts.entityKind] = "SingleStoreJson";
	getSQLType() {
		return "json";
	}
	mapToDriverValue = (value) => {
		return JSON.stringify(value);
	};
};
function json(name) {
	return new SingleStoreJsonBuilder(name ?? "");
}

//#endregion
exports.SingleStoreJson = SingleStoreJson;
exports.SingleStoreJsonBuilder = SingleStoreJsonBuilder;
exports.json = json;
//# sourceMappingURL=json.cjs.map