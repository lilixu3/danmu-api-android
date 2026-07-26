Object.defineProperty(exports, Symbol.toStringTag, { value: 'Module' });
const require_runtime = require('../../_virtual/_rolldown/runtime.cjs');
const require_cockroach_core_columns_common = require('./common.cjs');
const require_cockroach_core_columns_date_common = require('./date.common.cjs');
let __entity_ts = require("../../entity.cjs");
let __utils_ts = require("../../utils.cjs");

//#region src/cockroach-core/columns/timestamp.ts
var CockroachTimestampBuilder = class extends require_cockroach_core_columns_date_common.CockroachDateColumnBaseBuilder {
	static [__entity_ts.entityKind] = "CockroachTimestampBuilder";
	constructor(name, withTimezone, precision) {
		super(name, "object date", "CockroachTimestamp");
		this.config.withTimezone = withTimezone;
		this.config.precision = precision;
	}
	/** @internal */
	build(table) {
		return new CockroachTimestamp(table, this.config);
	}
};
var CockroachTimestamp = class extends require_cockroach_core_columns_common.CockroachColumn {
	static [__entity_ts.entityKind] = "CockroachTimestamp";
	withTimezone;
	precision;
	constructor(table, config) {
		super(table, config);
		this.withTimezone = config.withTimezone;
		this.precision = config.precision;
	}
	getSQLType() {
		const precision = this.precision === void 0 ? "" : `(${this.precision})`;
		return `timestamp${this.withTimezone ? "tz" : ""}${precision}`;
	}
	mapFromDriverValue = (value) => {
		return new Date(this.withTimezone ? value : value + "+0000");
	};
	mapToDriverValue = (value) => {
		if (typeof value === "string") return value;
		return value.toISOString();
	};
};
var CockroachTimestampStringBuilder = class extends require_cockroach_core_columns_date_common.CockroachDateColumnBaseBuilder {
	static [__entity_ts.entityKind] = "CockroachTimestampStringBuilder";
	constructor(name, withTimezone, precision) {
		super(name, "string timestamp", "CockroachTimestampString");
		this.config.withTimezone = withTimezone;
		this.config.precision = precision;
	}
	/** @internal */
	build(table) {
		return new CockroachTimestampString(table, this.config);
	}
};
var CockroachTimestampString = class extends require_cockroach_core_columns_common.CockroachColumn {
	static [__entity_ts.entityKind] = "CockroachTimestampString";
	withTimezone;
	precision;
	constructor(table, config) {
		super(table, config);
		this.withTimezone = config.withTimezone;
		this.precision = config.precision;
	}
	getSQLType() {
		const precision = this.precision === void 0 ? "" : `(${this.precision})`;
		return `timestamp${this.withTimezone ? "tz" : ""}${precision}`;
	}
	mapToDriverValue = (value) => {
		if (typeof value === "string") return value;
		return value.toISOString();
	};
};
function timestamp(a, b = {}) {
	const { name, config } = (0, __utils_ts.getColumnNameAndConfig)(a, b);
	if (config?.mode === "string") return new CockroachTimestampStringBuilder(name, config.withTimezone ?? false, config.precision);
	return new CockroachTimestampBuilder(name, config?.withTimezone ?? false, config?.precision);
}

//#endregion
exports.CockroachTimestamp = CockroachTimestamp;
exports.CockroachTimestampBuilder = CockroachTimestampBuilder;
exports.CockroachTimestampString = CockroachTimestampString;
exports.CockroachTimestampStringBuilder = CockroachTimestampStringBuilder;
exports.timestamp = timestamp;
//# sourceMappingURL=timestamp.cjs.map