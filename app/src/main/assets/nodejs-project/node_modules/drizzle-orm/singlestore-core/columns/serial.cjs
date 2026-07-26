Object.defineProperty(exports, Symbol.toStringTag, { value: 'Module' });
const require_runtime = require('../../_virtual/_rolldown/runtime.cjs');
const require_singlestore_core_columns_common = require('./common.cjs');
let __entity_ts = require("../../entity.cjs");

//#region src/singlestore-core/columns/serial.ts
var SingleStoreSerialBuilder = class extends require_singlestore_core_columns_common.SingleStoreColumnBuilderWithAutoIncrement {
	static [__entity_ts.entityKind] = "SingleStoreSerialBuilder";
	constructor(name) {
		super(name, "number uint53", "SingleStoreSerial");
		this.config.hasDefault = true;
		this.config.autoIncrement = true;
	}
	/** @internal */
	build(table) {
		return new SingleStoreSerial(table, this.config);
	}
};
var SingleStoreSerial = class extends require_singlestore_core_columns_common.SingleStoreColumnWithAutoIncrement {
	static [__entity_ts.entityKind] = "SingleStoreSerial";
	getSQLType() {
		return "serial";
	}
	mapFromDriverValue = (value) => {
		if (typeof value === "string") return Number(value);
		return value;
	};
};
function serial(name) {
	return new SingleStoreSerialBuilder(name ?? "");
}

//#endregion
exports.SingleStoreSerial = SingleStoreSerial;
exports.SingleStoreSerialBuilder = SingleStoreSerialBuilder;
exports.serial = serial;
//# sourceMappingURL=serial.cjs.map