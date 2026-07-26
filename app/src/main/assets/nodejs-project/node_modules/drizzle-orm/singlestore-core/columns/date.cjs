Object.defineProperty(exports, Symbol.toStringTag, { value: 'Module' });
const require_runtime = require('../../_virtual/_rolldown/runtime.cjs');
const require_singlestore_core_columns_common = require('./common.cjs');
let __entity_ts = require("../../entity.cjs");
let __utils_ts = require("../../utils.cjs");

//#region src/singlestore-core/columns/date.ts
var SingleStoreDateBuilder = class extends require_singlestore_core_columns_common.SingleStoreColumnBuilder {
	static [__entity_ts.entityKind] = "SingleStoreDateBuilder";
	constructor(name) {
		super(name, "object date", "SingleStoreDate");
	}
	/** @internal */
	build(table) {
		return new SingleStoreDate(table, this.config);
	}
};
var SingleStoreDate = class extends require_singlestore_core_columns_common.SingleStoreColumn {
	static [__entity_ts.entityKind] = "SingleStoreDate";
	constructor(table, config) {
		super(table, config);
	}
	getSQLType() {
		return `date`;
	}
	mapFromDriverValue = (value) => {
		return new Date(value);
	};
};
var SingleStoreDateStringBuilder = class extends require_singlestore_core_columns_common.SingleStoreColumnBuilder {
	static [__entity_ts.entityKind] = "SingleStoreDateStringBuilder";
	constructor(name) {
		super(name, "string date", "SingleStoreDateString");
	}
	/** @internal */
	build(table) {
		return new SingleStoreDateString(table, this.config);
	}
};
var SingleStoreDateString = class extends require_singlestore_core_columns_common.SingleStoreColumn {
	static [__entity_ts.entityKind] = "SingleStoreDateString";
	constructor(table, config) {
		super(table, config);
	}
	getSQLType() {
		return `date`;
	}
};
function date(a, b) {
	const { name, config } = (0, __utils_ts.getColumnNameAndConfig)(a, b);
	if (config?.mode === "string") return new SingleStoreDateStringBuilder(name);
	return new SingleStoreDateBuilder(name);
}

//#endregion
exports.SingleStoreDate = SingleStoreDate;
exports.SingleStoreDateBuilder = SingleStoreDateBuilder;
exports.SingleStoreDateString = SingleStoreDateString;
exports.SingleStoreDateStringBuilder = SingleStoreDateStringBuilder;
exports.date = date;
//# sourceMappingURL=date.cjs.map