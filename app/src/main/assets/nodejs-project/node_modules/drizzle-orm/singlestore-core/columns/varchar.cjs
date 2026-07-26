Object.defineProperty(exports, Symbol.toStringTag, { value: 'Module' });
const require_runtime = require('../../_virtual/_rolldown/runtime.cjs');
const require_singlestore_core_columns_common = require('./common.cjs');
let __entity_ts = require("../../entity.cjs");
let __utils_ts = require("../../utils.cjs");

//#region src/singlestore-core/columns/varchar.ts
var SingleStoreVarCharBuilder = class extends require_singlestore_core_columns_common.SingleStoreColumnBuilder {
	static [__entity_ts.entityKind] = "SingleStoreVarCharBuilder";
	/** @internal */
	constructor(name, config) {
		super(name, config.enum?.length ? "string enum" : "string", "SingleStoreVarChar");
		this.config.length = config.length;
		this.config.enum = config.enum;
	}
	/** @internal */
	build(table) {
		return new SingleStoreVarChar(table, this.config);
	}
};
var SingleStoreVarChar = class extends require_singlestore_core_columns_common.SingleStoreColumn {
	static [__entity_ts.entityKind] = "SingleStoreVarChar";
	enumValues = this.config.enum;
	getSQLType() {
		return this.length === void 0 ? `varchar` : `varchar(${this.length})`;
	}
};
function varchar(a, b) {
	const { name, config } = (0, __utils_ts.getColumnNameAndConfig)(a, b);
	return new SingleStoreVarCharBuilder(name, config);
}

//#endregion
exports.SingleStoreVarChar = SingleStoreVarChar;
exports.SingleStoreVarCharBuilder = SingleStoreVarCharBuilder;
exports.varchar = varchar;
//# sourceMappingURL=varchar.cjs.map