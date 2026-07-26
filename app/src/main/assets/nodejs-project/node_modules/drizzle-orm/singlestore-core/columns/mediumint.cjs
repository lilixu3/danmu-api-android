Object.defineProperty(exports, Symbol.toStringTag, { value: 'Module' });
const require_runtime = require('../../_virtual/_rolldown/runtime.cjs');
const require_singlestore_core_columns_common = require('./common.cjs');
let __entity_ts = require("../../entity.cjs");
let __utils_ts = require("../../utils.cjs");

//#region src/singlestore-core/columns/mediumint.ts
var SingleStoreMediumIntBuilder = class extends require_singlestore_core_columns_common.SingleStoreColumnBuilderWithAutoIncrement {
	static [__entity_ts.entityKind] = "SingleStoreMediumIntBuilder";
	constructor(name, config) {
		super(name, config?.unsigned ? "number uint24" : "number int24", "SingleStoreMediumInt");
		this.config.unsigned = config ? config.unsigned : false;
	}
	/** @internal */
	build(table) {
		return new SingleStoreMediumInt(table, this.config);
	}
};
var SingleStoreMediumInt = class extends require_singlestore_core_columns_common.SingleStoreColumnWithAutoIncrement {
	static [__entity_ts.entityKind] = "SingleStoreMediumInt";
	getSQLType() {
		return `mediumint${this.config.unsigned ? " unsigned" : ""}`;
	}
	mapFromDriverValue = (value) => {
		if (typeof value === "string") return Number(value);
		return value;
	};
};
function mediumint(a, b) {
	const { name, config } = (0, __utils_ts.getColumnNameAndConfig)(a, b);
	return new SingleStoreMediumIntBuilder(name, config);
}

//#endregion
exports.SingleStoreMediumInt = SingleStoreMediumInt;
exports.SingleStoreMediumIntBuilder = SingleStoreMediumIntBuilder;
exports.mediumint = mediumint;
//# sourceMappingURL=mediumint.cjs.map