Object.defineProperty(exports, Symbol.toStringTag, { value: 'Module' });
const require_runtime = require('../../_virtual/_rolldown/runtime.cjs');
const require_singlestore_core_columns_common = require('./common.cjs');
let __entity_ts = require("../../entity.cjs");
let __utils_ts = require("../../utils.cjs");

//#region src/singlestore-core/columns/varbinary.ts
var SingleStoreVarBinaryBuilder = class extends require_singlestore_core_columns_common.SingleStoreColumnBuilder {
	static [__entity_ts.entityKind] = "SingleStoreVarBinaryBuilder";
	/** @internal */
	constructor(name, config) {
		super(name, "string binary", "SingleStoreVarBinary");
		this.config.length = config.length;
	}
	/** @internal */
	build(table) {
		return new SingleStoreVarBinary(table, this.config);
	}
};
var SingleStoreVarBinary = class extends require_singlestore_core_columns_common.SingleStoreColumn {
	static [__entity_ts.entityKind] = "SingleStoreVarBinary";
	mapFromDriverValue = (value) => {
		if (typeof value === "string") return value;
		if (Buffer.isBuffer(value)) return value.toString();
		const str = [];
		for (const v of value) str.push(v === 49 ? "1" : "0");
		return str.join("");
	};
	getSQLType() {
		return this.length === void 0 ? `varbinary` : `varbinary(${this.length})`;
	}
};
function varbinary(a, b) {
	const { name, config } = (0, __utils_ts.getColumnNameAndConfig)(a, b);
	return new SingleStoreVarBinaryBuilder(name, config);
}

//#endregion
exports.SingleStoreVarBinary = SingleStoreVarBinary;
exports.SingleStoreVarBinaryBuilder = SingleStoreVarBinaryBuilder;
exports.varbinary = varbinary;
//# sourceMappingURL=varbinary.cjs.map