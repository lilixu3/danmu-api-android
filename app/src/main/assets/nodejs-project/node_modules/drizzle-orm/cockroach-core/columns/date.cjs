Object.defineProperty(exports, Symbol.toStringTag, { value: 'Module' });
const require_runtime = require('../../_virtual/_rolldown/runtime.cjs');
const require_cockroach_core_columns_common = require('./common.cjs');
const require_cockroach_core_columns_date_common = require('./date.common.cjs');
let __entity_ts = require("../../entity.cjs");
let __utils_ts = require("../../utils.cjs");

//#region src/cockroach-core/columns/date.ts
var CockroachDateBuilder = class extends require_cockroach_core_columns_date_common.CockroachDateColumnBaseBuilder {
	static [__entity_ts.entityKind] = "CockroachDateBuilder";
	constructor(name) {
		super(name, "object date", "CockroachDate");
	}
	/** @internal */
	build(table) {
		return new CockroachDate(table, this.config);
	}
};
var CockroachDate = class extends require_cockroach_core_columns_common.CockroachColumn {
	static [__entity_ts.entityKind] = "CockroachDate";
	getSQLType() {
		return "date";
	}
	mapFromDriverValue = (value) => {
		return new Date(value);
	};
	mapToDriverValue = (value) => {
		if (typeof value === "string") return value;
		return value.toISOString();
	};
};
var CockroachDateStringBuilder = class extends require_cockroach_core_columns_date_common.CockroachDateColumnBaseBuilder {
	static [__entity_ts.entityKind] = "CockroachDateStringBuilder";
	constructor(name) {
		super(name, "string date", "CockroachDateString");
	}
	/** @internal */
	build(table) {
		return new CockroachDateString(table, this.config);
	}
};
var CockroachDateString = class extends require_cockroach_core_columns_common.CockroachColumn {
	static [__entity_ts.entityKind] = "CockroachDateString";
	getSQLType() {
		return "date";
	}
	mapToDriverValue = (value) => {
		if (typeof value === "string") return value;
		return value.toISOString();
	};
};
function date(a, b) {
	const { name, config } = (0, __utils_ts.getColumnNameAndConfig)(a, b);
	if (config?.mode === "date") return new CockroachDateBuilder(name);
	return new CockroachDateStringBuilder(name);
}

//#endregion
exports.CockroachDate = CockroachDate;
exports.CockroachDateBuilder = CockroachDateBuilder;
exports.CockroachDateString = CockroachDateString;
exports.CockroachDateStringBuilder = CockroachDateStringBuilder;
exports.date = date;
//# sourceMappingURL=date.cjs.map