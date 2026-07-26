Object.defineProperty(exports, Symbol.toStringTag, { value: 'Module' });
const require_runtime = require('../../_virtual/_rolldown/runtime.cjs');
const require_singlestore_core_columns_common = require('./common.cjs');
let __entity_ts = require("../../entity.cjs");
let __utils_ts = require("../../utils.cjs");

//#region src/singlestore-core/columns/binary.ts
var SingleStoreBinaryBuilder = class extends require_singlestore_core_columns_common.SingleStoreColumnBuilder {
	static [__entity_ts.entityKind] = "SingleStoreBinaryBuilder";
	constructor(name, length) {
		super(name, "string binary", "SingleStoreBinary");
		this.config.length = length ?? 1;
		this.config.setLength = length !== void 0;
	}
	/** @internal */
	build(table) {
		return new SingleStoreBinary(table, this.config);
	}
};
var SingleStoreBinary = class extends require_singlestore_core_columns_common.SingleStoreColumn {
	static [__entity_ts.entityKind] = "SingleStoreBinary";
	mapFromDriverValue = (value) => {
		if (typeof value === "string") return value;
		if (Buffer.isBuffer(value)) return value.toString();
		const str = [];
		for (const v of value) str.push(v === 49 ? "1" : "0");
		return str.join("");
	};
	getSQLType() {
		return this.config.setLength ? `binary(${this.length})` : `binary`;
	}
};
function binary(a, b = {}) {
	const { name, config } = (0, __utils_ts.getColumnNameAndConfig)(a, b);
	return new SingleStoreBinaryBuilder(name, config.length);
}

//#endregion
exports.SingleStoreBinary = SingleStoreBinary;
exports.SingleStoreBinaryBuilder = SingleStoreBinaryBuilder;
exports.binary = binary;
//# sourceMappingURL=binary.cjs.map