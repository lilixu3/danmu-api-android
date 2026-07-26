Object.defineProperty(exports, Symbol.toStringTag, { value: 'Module' });
const require_runtime = require('../../_virtual/_rolldown/runtime.cjs');
const require_singlestore_core_columns_common = require('./common.cjs');
let __entity_ts = require("../../entity.cjs");

//#region src/singlestore-core/columns/time.ts
var SingleStoreTimeBuilder = class extends require_singlestore_core_columns_common.SingleStoreColumnBuilder {
	static [__entity_ts.entityKind] = "SingleStoreTimeBuilder";
	constructor(name) {
		super(name, "string time", "SingleStoreTime");
	}
	/** @internal */
	build(table) {
		return new SingleStoreTime(table, this.config);
	}
};
var SingleStoreTime = class extends require_singlestore_core_columns_common.SingleStoreColumn {
	static [__entity_ts.entityKind] = "SingleStoreTime";
	getSQLType() {
		return `time`;
	}
};
function time(name) {
	return new SingleStoreTimeBuilder(name ?? "");
}

//#endregion
exports.SingleStoreTime = SingleStoreTime;
exports.SingleStoreTimeBuilder = SingleStoreTimeBuilder;
exports.time = time;
//# sourceMappingURL=time.cjs.map