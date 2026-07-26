Object.defineProperty(exports, Symbol.toStringTag, { value: 'Module' });
const require_runtime = require('../../_virtual/_rolldown/runtime.cjs');
const require_singlestore_core_columns_date_common = require('./date.common.cjs');
let __entity_ts = require("../../entity.cjs");
let __utils_ts = require("../../utils.cjs");
let __sql_sql_ts = require("../../sql/sql.cjs");

//#region src/singlestore-core/columns/timestamp.ts
var SingleStoreTimestampBuilder = class extends require_singlestore_core_columns_date_common.SingleStoreDateColumnBaseBuilder {
	static [__entity_ts.entityKind] = "SingleStoreTimestampBuilder";
	constructor(name) {
		super(name, "object date", "SingleStoreTimestamp");
	}
	/** @internal */
	build(table) {
		return new SingleStoreTimestamp(table, this.config);
	}
	defaultNow() {
		return this.default(__sql_sql_ts.sql`CURRENT_TIMESTAMP`);
	}
};
var SingleStoreTimestamp = class extends require_singlestore_core_columns_date_common.SingleStoreDateBaseColumn {
	static [__entity_ts.entityKind] = "SingleStoreTimestamp";
	getSQLType() {
		return `timestamp`;
	}
	mapFromDriverValue = (value) => {
		return /* @__PURE__ */ new Date(value + "+0000");
	};
	mapToDriverValue = (value) => {
		if (typeof value === "string") return value;
		return value.toISOString().slice(0, -1).replace("T", " ");
	};
};
var SingleStoreTimestampStringBuilder = class extends require_singlestore_core_columns_date_common.SingleStoreDateColumnBaseBuilder {
	static [__entity_ts.entityKind] = "SingleStoreTimestampStringBuilder";
	constructor(name) {
		super(name, "string timestamp", "SingleStoreTimestampString");
	}
	/** @internal */
	build(table) {
		return new SingleStoreTimestampString(table, this.config);
	}
	defaultNow() {
		return this.default(__sql_sql_ts.sql`CURRENT_TIMESTAMP`);
	}
};
var SingleStoreTimestampString = class extends require_singlestore_core_columns_date_common.SingleStoreDateBaseColumn {
	static [__entity_ts.entityKind] = "SingleStoreTimestampString";
	getSQLType() {
		return `timestamp`;
	}
	mapToDriverValue = (value) => {
		if (typeof value === "string") return value;
		return value.toISOString().slice(0, -1).replace("T", " ");
	};
};
function timestamp(a, b = {}) {
	const { name, config } = (0, __utils_ts.getColumnNameAndConfig)(a, b);
	if (config?.mode === "string") return new SingleStoreTimestampStringBuilder(name);
	return new SingleStoreTimestampBuilder(name);
}

//#endregion
exports.SingleStoreTimestamp = SingleStoreTimestamp;
exports.SingleStoreTimestampBuilder = SingleStoreTimestampBuilder;
exports.SingleStoreTimestampString = SingleStoreTimestampString;
exports.SingleStoreTimestampStringBuilder = SingleStoreTimestampStringBuilder;
exports.timestamp = timestamp;
//# sourceMappingURL=timestamp.cjs.map