Object.defineProperty(exports, Symbol.toStringTag, { value: 'Module' });
const require_runtime = require('../../_virtual/_rolldown/runtime.cjs');
const require_pg_core_columns_common = require('./common.cjs');
const require_pg_core_columns_date_common = require('./date.common.cjs');
let __entity_ts = require("../../entity.cjs");
let __utils_ts = require("../../utils.cjs");

//#region src/pg-core/columns/timestamp.ts
var PgTimestampBuilder = class extends require_pg_core_columns_date_common.PgDateColumnBuilder {
	static [__entity_ts.entityKind] = "PgTimestampBuilder";
	constructor(name, withTimezone, precision) {
		super(name, "object date", "PgTimestamp");
		this.config.withTimezone = withTimezone;
		this.config.precision = precision;
	}
	/** @internal */
	build(table) {
		return new PgTimestamp(table, this.config);
	}
};
var PgTimestamp = class extends require_pg_core_columns_common.PgColumn {
	static [__entity_ts.entityKind] = "PgTimestamp";
	/** @internal */
	codec;
	withTimezone;
	precision;
	constructor(table, config) {
		super(table, config);
		this.withTimezone = config.withTimezone;
		this.precision = config.precision;
		this.codec = this.withTimezone ? "timestamptz" : "timestamp";
	}
	getSQLType() {
		return `timestamp${this.precision === void 0 ? "" : ` (${this.precision})`}${this.withTimezone ? " with time zone" : ""}`;
	}
	mapToDriverValue = (value) => {
		if (typeof value === "string") return value;
		return value.toISOString();
	};
};
var PgTimestampStringBuilder = class extends require_pg_core_columns_date_common.PgDateColumnBuilder {
	static [__entity_ts.entityKind] = "PgTimestampStringBuilder";
	constructor(name, withTimezone, precision) {
		super(name, "string timestamp", "PgTimestampString");
		this.config.withTimezone = withTimezone;
		this.config.precision = precision;
	}
	/** @internal */
	build(table) {
		return new PgTimestampString(table, this.config);
	}
};
var PgTimestampString = class extends require_pg_core_columns_common.PgColumn {
	static [__entity_ts.entityKind] = "PgTimestampString";
	/** @internal */
	codec;
	withTimezone;
	precision;
	constructor(table, config) {
		super(table, config);
		this.withTimezone = config.withTimezone;
		this.precision = config.precision;
		this.codec = this.withTimezone ? "timestamptz:string" : "timestamp:string";
	}
	getSQLType() {
		return `timestamp${this.precision === void 0 ? "" : `(${this.precision})`}${this.withTimezone ? " with time zone" : ""}`;
	}
	mapToDriverValue = (value) => {
		if (typeof value === "string") return value;
		return value.toISOString();
	};
};
function timestamp(a, b = {}) {
	const { name, config } = (0, __utils_ts.getColumnNameAndConfig)(a, b);
	if (config?.mode === "string") return new PgTimestampStringBuilder(name, config.withTimezone ?? false, config.precision);
	return new PgTimestampBuilder(name, config?.withTimezone ?? false, config?.precision);
}

//#endregion
exports.PgTimestamp = PgTimestamp;
exports.PgTimestampBuilder = PgTimestampBuilder;
exports.PgTimestampString = PgTimestampString;
exports.PgTimestampStringBuilder = PgTimestampStringBuilder;
exports.timestamp = timestamp;
//# sourceMappingURL=timestamp.cjs.map