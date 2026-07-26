Object.defineProperty(exports, Symbol.toStringTag, { value: 'Module' });
const require_runtime = require('../../_virtual/_rolldown/runtime.cjs');
const require_pg_core_columns_common = require('./common.cjs');
const require_pg_core_columns_date_common = require('./date.common.cjs');
let __entity_ts = require("../../entity.cjs");
let __utils_ts = require("../../utils.cjs");

//#region src/pg-core/columns/time.ts
var PgTimeBuilder = class extends require_pg_core_columns_date_common.PgDateColumnBuilder {
	static [__entity_ts.entityKind] = "PgTimeBuilder";
	constructor(name, withTimezone, precision) {
		super(name, "string time", "PgTime");
		this.withTimezone = withTimezone;
		this.precision = precision;
		this.config.withTimezone = withTimezone;
		this.config.precision = precision;
	}
	/** @internal */
	build(table) {
		return new PgTime(table, this.config);
	}
};
var PgTime = class extends require_pg_core_columns_common.PgColumn {
	static [__entity_ts.entityKind] = "PgTime";
	/** @internal */
	codec = "time";
	withTimezone;
	precision;
	constructor(table, config) {
		super(table, config);
		this.withTimezone = config.withTimezone;
		this.precision = config.precision;
	}
	getSQLType() {
		return `time${this.precision === void 0 ? "" : `(${this.precision})`}${this.withTimezone ? " with time zone" : ""}`;
	}
};
function time(a, b = {}) {
	const { name, config } = (0, __utils_ts.getColumnNameAndConfig)(a, b);
	return new PgTimeBuilder(name, config.withTimezone ?? false, config.precision);
}

//#endregion
exports.PgTime = PgTime;
exports.PgTimeBuilder = PgTimeBuilder;
exports.time = time;
//# sourceMappingURL=time.cjs.map