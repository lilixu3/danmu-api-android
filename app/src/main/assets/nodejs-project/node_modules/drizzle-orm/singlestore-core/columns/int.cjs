Object.defineProperty(exports, Symbol.toStringTag, { value: 'Module' });
const require_runtime = require('../../_virtual/_rolldown/runtime.cjs');
const require_singlestore_core_columns_common = require('./common.cjs');
let __entity_ts = require("../../entity.cjs");
let __utils_ts = require("../../utils.cjs");

//#region src/singlestore-core/columns/int.ts
var SingleStoreIntBuilder = class extends require_singlestore_core_columns_common.SingleStoreColumnBuilderWithAutoIncrement {
	static [__entity_ts.entityKind] = "SingleStoreIntBuilder";
	constructor(name, config) {
		super(name, config?.unsigned ? "number uint32" : "number int32", "SingleStoreInt");
		this.config.unsigned = config ? config.unsigned : false;
	}
	/** @internal */
	build(table) {
		return new SingleStoreInt(table, this.config);
	}
};
var SingleStoreInt = class extends require_singlestore_core_columns_common.SingleStoreColumnWithAutoIncrement {
	static [__entity_ts.entityKind] = "SingleStoreInt";
	getSQLType() {
		return `int${this.config.unsigned ? " unsigned" : ""}`;
	}
	mapFromDriverValue = (value) => {
		if (typeof value === "string") return Number(value);
		return value;
	};
};
function int(a, b) {
	const { name, config } = (0, __utils_ts.getColumnNameAndConfig)(a, b);
	return new SingleStoreIntBuilder(name, config);
}

//#endregion
exports.SingleStoreInt = SingleStoreInt;
exports.SingleStoreIntBuilder = SingleStoreIntBuilder;
exports.int = int;
//# sourceMappingURL=int.cjs.map