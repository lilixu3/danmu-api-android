Object.defineProperty(exports, Symbol.toStringTag, { value: 'Module' });
const require_runtime = require('../../_virtual/_rolldown/runtime.cjs');
const require_singlestore_core_columns_common = require('./common.cjs');
let __entity_ts = require("../../entity.cjs");

//#region src/singlestore-core/columns/boolean.ts
var SingleStoreBooleanBuilder = class extends require_singlestore_core_columns_common.SingleStoreColumnBuilder {
	static [__entity_ts.entityKind] = "SingleStoreBooleanBuilder";
	constructor(name) {
		super(name, "boolean", "SingleStoreBoolean");
	}
	/** @internal */
	build(table) {
		return new SingleStoreBoolean(table, this.config);
	}
};
var SingleStoreBoolean = class extends require_singlestore_core_columns_common.SingleStoreColumn {
	static [__entity_ts.entityKind] = "SingleStoreBoolean";
	getSQLType() {
		return "boolean";
	}
	mapFromDriverValue = (value) => {
		if (typeof value === "boolean") return value;
		return value === 1;
	};
};
function boolean(name) {
	return new SingleStoreBooleanBuilder(name ?? "");
}

//#endregion
exports.SingleStoreBoolean = SingleStoreBoolean;
exports.SingleStoreBooleanBuilder = SingleStoreBooleanBuilder;
exports.boolean = boolean;
//# sourceMappingURL=boolean.cjs.map