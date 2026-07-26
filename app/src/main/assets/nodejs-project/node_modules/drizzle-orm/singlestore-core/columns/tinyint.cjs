Object.defineProperty(exports, Symbol.toStringTag, { value: 'Module' });
const require_runtime = require('../../_virtual/_rolldown/runtime.cjs');
const require_singlestore_core_columns_common = require('./common.cjs');
let __entity_ts = require("../../entity.cjs");
let __utils_ts = require("../../utils.cjs");

//#region src/singlestore-core/columns/tinyint.ts
var SingleStoreTinyIntBuilder = class extends require_singlestore_core_columns_common.SingleStoreColumnBuilderWithAutoIncrement {
	static [__entity_ts.entityKind] = "SingleStoreTinyIntBuilder";
	constructor(name, config) {
		super(name, config?.unsigned ? "number uint8" : "number int8", "SingleStoreTinyInt");
		this.config.unsigned = config ? config.unsigned : false;
	}
	/** @internal */
	build(table) {
		return new SingleStoreTinyInt(table, this.config);
	}
};
var SingleStoreTinyInt = class extends require_singlestore_core_columns_common.SingleStoreColumnWithAutoIncrement {
	static [__entity_ts.entityKind] = "SingleStoreTinyInt";
	getSQLType() {
		return `tinyint${this.config.unsigned ? " unsigned" : ""}`;
	}
	mapFromDriverValue = (value) => {
		if (typeof value === "string") return Number(value);
		return value;
	};
};
function tinyint(a, b) {
	const { name, config } = (0, __utils_ts.getColumnNameAndConfig)(a, b);
	return new SingleStoreTinyIntBuilder(name, config);
}

//#endregion
exports.SingleStoreTinyInt = SingleStoreTinyInt;
exports.SingleStoreTinyIntBuilder = SingleStoreTinyIntBuilder;
exports.tinyint = tinyint;
//# sourceMappingURL=tinyint.cjs.map