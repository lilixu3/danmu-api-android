Object.defineProperty(exports, Symbol.toStringTag, { value: 'Module' });
const require_runtime = require('../../_virtual/_rolldown/runtime.cjs');
const require_singlestore_core_columns_common = require('./common.cjs');
let __entity_ts = require("../../entity.cjs");
let __utils_ts = require("../../utils.cjs");

//#region src/singlestore-core/columns/datetime.ts
var SingleStoreDateTimeBuilder = class extends require_singlestore_core_columns_common.SingleStoreColumnBuilder {
	generatedAlwaysAs(as, config) {
		throw new Error("Method not implemented.");
	}
	static [__entity_ts.entityKind] = "SingleStoreDateTimeBuilder";
	constructor(name) {
		super(name, "object date", "SingleStoreDateTime");
	}
	/** @internal */
	build(table) {
		return new SingleStoreDateTime(table, this.config);
	}
};
var SingleStoreDateTime = class extends require_singlestore_core_columns_common.SingleStoreColumn {
	static [__entity_ts.entityKind] = "SingleStoreDateTime";
	constructor(table, config) {
		super(table, config);
	}
	getSQLType() {
		return `datetime`;
	}
	mapToDriverValue = (value) => {
		if (typeof value === "string") return value;
		return value.toISOString().replace("T", " ").replace("Z", "");
	};
	mapFromDriverValue = (value) => {
		return /* @__PURE__ */ new Date(value.replace(" ", "T") + "Z");
	};
};
var SingleStoreDateTimeStringBuilder = class extends require_singlestore_core_columns_common.SingleStoreColumnBuilder {
	generatedAlwaysAs(_as, _config) {
		throw new Error("Method not implemented.");
	}
	static [__entity_ts.entityKind] = "SingleStoreDateTimeStringBuilder";
	constructor(name) {
		super(name, "string datetime", "SingleStoreDateTimeString");
	}
	/** @internal */
	build(table) {
		return new SingleStoreDateTimeString(table, this.config);
	}
};
var SingleStoreDateTimeString = class extends require_singlestore_core_columns_common.SingleStoreColumn {
	static [__entity_ts.entityKind] = "SingleStoreDateTimeString";
	constructor(table, config) {
		super(table, config);
	}
	getSQLType() {
		return `datetime`;
	}
	mapToDriverValue = (value) => {
		if (typeof value === "string") return value;
		return value.toISOString().replace("T", " ").replace("Z", "");
	};
};
function datetime(a, b) {
	const { name, config } = (0, __utils_ts.getColumnNameAndConfig)(a, b);
	if (config?.mode === "string") return new SingleStoreDateTimeStringBuilder(name);
	return new SingleStoreDateTimeBuilder(name);
}

//#endregion
exports.SingleStoreDateTime = SingleStoreDateTime;
exports.SingleStoreDateTimeBuilder = SingleStoreDateTimeBuilder;
exports.SingleStoreDateTimeString = SingleStoreDateTimeString;
exports.SingleStoreDateTimeStringBuilder = SingleStoreDateTimeStringBuilder;
exports.datetime = datetime;
//# sourceMappingURL=datetime.cjs.map