Object.defineProperty(exports, Symbol.toStringTag, { value: 'Module' });
const require_runtime = require('../../_virtual/_rolldown/runtime.cjs');
const require_singlestore_core_columns_common = require('./common.cjs');
let __entity_ts = require("../../entity.cjs");
let __utils_ts = require("../../utils.cjs");

//#region src/singlestore-core/columns/enum.ts
var SingleStoreEnumColumnBuilder = class extends require_singlestore_core_columns_common.SingleStoreColumnBuilder {
	generatedAlwaysAs(as, config) {
		throw new Error("Method not implemented.");
	}
	static [__entity_ts.entityKind] = "SingleStoreEnumColumnBuilder";
	constructor(name, values) {
		super(name, "string enum", "SingleStoreEnumColumn");
		this.config.enumValues = values;
	}
	/** @internal */
	build(table) {
		return new SingleStoreEnumColumn(table, this.config);
	}
};
var SingleStoreEnumColumn = class extends require_singlestore_core_columns_common.SingleStoreColumn {
	static [__entity_ts.entityKind] = "SingleStoreEnumColumn";
	enumValues = this.config.enumValues;
	getSQLType() {
		return `enum(${this.enumValues.map((value) => `'${value}'`).join(",")})`;
	}
};
function singlestoreEnum(a, b) {
	const { name, config: values } = (0, __utils_ts.getColumnNameAndConfig)(a, b);
	if (values.length === 0) throw new Error(`You have an empty array for "${name}" enum values`);
	return new SingleStoreEnumColumnBuilder(name, values);
}

//#endregion
exports.SingleStoreEnumColumn = SingleStoreEnumColumn;
exports.SingleStoreEnumColumnBuilder = SingleStoreEnumColumnBuilder;
exports.singlestoreEnum = singlestoreEnum;
//# sourceMappingURL=enum.cjs.map