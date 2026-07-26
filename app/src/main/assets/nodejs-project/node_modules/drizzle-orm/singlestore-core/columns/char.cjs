Object.defineProperty(exports, Symbol.toStringTag, { value: 'Module' });
const require_runtime = require('../../_virtual/_rolldown/runtime.cjs');
const require_singlestore_core_columns_common = require('./common.cjs');
let __entity_ts = require("../../entity.cjs");
let __utils_ts = require("../../utils.cjs");

//#region src/singlestore-core/columns/char.ts
var SingleStoreCharBuilder = class extends require_singlestore_core_columns_common.SingleStoreColumnBuilder {
	static [__entity_ts.entityKind] = "SingleStoreCharBuilder";
	constructor(name, config) {
		super(name, config.enum?.length ? "string enum" : "string", "SingleStoreChar");
		this.config.length = config.length ?? 1;
		this.config.setLength = config.length !== void 0;
		this.config.enum = config.enum;
	}
	/** @internal */
	build(table) {
		return new SingleStoreChar(table, this.config);
	}
};
var SingleStoreChar = class extends require_singlestore_core_columns_common.SingleStoreColumn {
	static [__entity_ts.entityKind] = "SingleStoreChar";
	enumValues = this.config.enum;
	getSQLType() {
		return this.config.setLength ? `char(${this.length})` : `char`;
	}
};
function char(a, b = {}) {
	const { name, config } = (0, __utils_ts.getColumnNameAndConfig)(a, b);
	return new SingleStoreCharBuilder(name, config);
}

//#endregion
exports.SingleStoreChar = SingleStoreChar;
exports.SingleStoreCharBuilder = SingleStoreCharBuilder;
exports.char = char;
//# sourceMappingURL=char.cjs.map