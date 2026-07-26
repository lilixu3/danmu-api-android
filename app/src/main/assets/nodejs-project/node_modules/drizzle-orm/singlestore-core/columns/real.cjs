Object.defineProperty(exports, Symbol.toStringTag, { value: 'Module' });
const require_runtime = require('../../_virtual/_rolldown/runtime.cjs');
const require_singlestore_core_columns_common = require('./common.cjs');
let __entity_ts = require("../../entity.cjs");
let __utils_ts = require("../../utils.cjs");

//#region src/singlestore-core/columns/real.ts
var SingleStoreRealBuilder = class extends require_singlestore_core_columns_common.SingleStoreColumnBuilderWithAutoIncrement {
	static [__entity_ts.entityKind] = "SingleStoreRealBuilder";
	constructor(name, config) {
		super(name, "number double", "SingleStoreReal");
		this.config.precision = config?.precision;
		this.config.scale = config?.scale;
	}
	/** @internal */
	build(table) {
		return new SingleStoreReal(table, this.config);
	}
};
var SingleStoreReal = class extends require_singlestore_core_columns_common.SingleStoreColumnWithAutoIncrement {
	static [__entity_ts.entityKind] = "SingleStoreReal";
	precision = this.config.precision;
	scale = this.config.scale;
	getSQLType() {
		if (this.precision !== void 0 && this.scale !== void 0) return `real(${this.precision}, ${this.scale})`;
		else if (this.precision === void 0) return "real";
		else return `real(${this.precision})`;
	}
};
function real(a, b = {}) {
	const { name, config } = (0, __utils_ts.getColumnNameAndConfig)(a, b);
	return new SingleStoreRealBuilder(name, config);
}

//#endregion
exports.SingleStoreReal = SingleStoreReal;
exports.SingleStoreRealBuilder = SingleStoreRealBuilder;
exports.real = real;
//# sourceMappingURL=real.cjs.map