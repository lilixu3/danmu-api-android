Object.defineProperty(exports, Symbol.toStringTag, { value: 'Module' });
const require_runtime = require('../../_virtual/_rolldown/runtime.cjs');
const require_singlestore_core_columns_common = require('./common.cjs');
let __entity_ts = require("../../entity.cjs");
let __utils_ts = require("../../utils.cjs");

//#region src/singlestore-core/columns/float.ts
var SingleStoreFloatBuilder = class extends require_singlestore_core_columns_common.SingleStoreColumnBuilderWithAutoIncrement {
	static [__entity_ts.entityKind] = "SingleStoreFloatBuilder";
	constructor(name, config) {
		super(name, config?.unsigned ? "number ufloat" : "number float", "SingleStoreFloat");
		this.config.precision = config?.precision;
		this.config.scale = config?.scale;
		this.config.unsigned = config?.unsigned;
	}
	/** @internal */
	build(table) {
		return new SingleStoreFloat(table, this.config);
	}
};
var SingleStoreFloat = class extends require_singlestore_core_columns_common.SingleStoreColumnWithAutoIncrement {
	static [__entity_ts.entityKind] = "SingleStoreFloat";
	precision = this.config.precision;
	scale = this.config.scale;
	unsigned = this.config.unsigned;
	getSQLType() {
		let type = "";
		if (this.precision !== void 0 && this.scale !== void 0) type += `float(${this.precision},${this.scale})`;
		else if (this.precision === void 0) type += "float";
		else type += `float(${this.precision},0)`;
		return this.unsigned ? `${type} unsigned` : type;
	}
	mapFromDriverValue = (value) => {
		if (typeof value !== "number") return Number(value);
		return value;
	};
};
function float(a, b) {
	const { name, config } = (0, __utils_ts.getColumnNameAndConfig)(a, b);
	return new SingleStoreFloatBuilder(name, config);
}

//#endregion
exports.SingleStoreFloat = SingleStoreFloat;
exports.SingleStoreFloatBuilder = SingleStoreFloatBuilder;
exports.float = float;
//# sourceMappingURL=float.cjs.map