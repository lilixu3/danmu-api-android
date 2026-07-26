Object.defineProperty(exports, Symbol.toStringTag, { value: 'Module' });
const require_runtime = require('../../_virtual/_rolldown/runtime.cjs');
const require_singlestore_core_columns_common = require('./common.cjs');
let __entity_ts = require("../../entity.cjs");
let __utils_ts = require("../../utils.cjs");

//#region src/singlestore-core/columns/smallint.ts
var SingleStoreSmallIntBuilder = class extends require_singlestore_core_columns_common.SingleStoreColumnBuilderWithAutoIncrement {
	static [__entity_ts.entityKind] = "SingleStoreSmallIntBuilder";
	constructor(name, config) {
		super(name, config?.unsigned ? "number uint16" : "number int16", "SingleStoreSmallInt");
		this.config.unsigned = config ? config.unsigned : false;
	}
	/** @internal */
	build(table) {
		return new SingleStoreSmallInt(table, this.config);
	}
};
var SingleStoreSmallInt = class extends require_singlestore_core_columns_common.SingleStoreColumnWithAutoIncrement {
	static [__entity_ts.entityKind] = "SingleStoreSmallInt";
	getSQLType() {
		return `smallint${this.config.unsigned ? " unsigned" : ""}`;
	}
	mapFromDriverValue = (value) => {
		if (typeof value === "string") return Number(value);
		return value;
	};
};
function smallint(a, b) {
	const { name, config } = (0, __utils_ts.getColumnNameAndConfig)(a, b);
	return new SingleStoreSmallIntBuilder(name, config);
}

//#endregion
exports.SingleStoreSmallInt = SingleStoreSmallInt;
exports.SingleStoreSmallIntBuilder = SingleStoreSmallIntBuilder;
exports.smallint = smallint;
//# sourceMappingURL=smallint.cjs.map