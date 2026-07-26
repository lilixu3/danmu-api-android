Object.defineProperty(exports, Symbol.toStringTag, { value: 'Module' });
const require_runtime = require('../../_virtual/_rolldown/runtime.cjs');
const require_singlestore_core_columns_common = require('./common.cjs');
let __entity_ts = require("../../entity.cjs");
let __utils_ts = require("../../utils.cjs");

//#region src/singlestore-core/columns/double.ts
var SingleStoreDoubleBuilder = class extends require_singlestore_core_columns_common.SingleStoreColumnBuilderWithAutoIncrement {
	static [__entity_ts.entityKind] = "SingleStoreDoubleBuilder";
	constructor(name, config) {
		super(name, config?.unsigned ? "number udouble" : "number double", "SingleStoreDouble");
		this.config.precision = config?.precision;
		this.config.scale = config?.scale;
		this.config.unsigned = config?.unsigned;
	}
	/** @internal */
	build(table) {
		return new SingleStoreDouble(table, this.config);
	}
};
var SingleStoreDouble = class extends require_singlestore_core_columns_common.SingleStoreColumnWithAutoIncrement {
	static [__entity_ts.entityKind] = "SingleStoreDouble";
	precision = this.config.precision;
	scale = this.config.scale;
	unsigned = this.config.unsigned;
	getSQLType() {
		let type = "";
		if (this.precision !== void 0 && this.scale !== void 0) type += `double(${this.precision},${this.scale})`;
		else if (this.precision === void 0) type += "double";
		else type += `double(${this.precision})`;
		return this.unsigned ? `${type} unsigned` : type;
	}
};
function double(a, b) {
	const { name, config } = (0, __utils_ts.getColumnNameAndConfig)(a, b);
	return new SingleStoreDoubleBuilder(name, config);
}

//#endregion
exports.SingleStoreDouble = SingleStoreDouble;
exports.SingleStoreDoubleBuilder = SingleStoreDoubleBuilder;
exports.double = double;
//# sourceMappingURL=double.cjs.map