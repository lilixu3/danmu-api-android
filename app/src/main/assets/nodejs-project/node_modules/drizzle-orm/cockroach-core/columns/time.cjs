Object.defineProperty(exports, Symbol.toStringTag, { value: 'Module' });
const require_runtime = require('../../_virtual/_rolldown/runtime.cjs');
const require_cockroach_core_columns_common = require('./common.cjs');
let __entity_ts = require("../../entity.cjs");
let __utils_ts = require("../../utils.cjs");

//#region src/cockroach-core/columns/time.ts
var CockroachTimeBuilder = class extends require_cockroach_core_columns_common.CockroachColumnWithArrayBuilder {
	static [__entity_ts.entityKind] = "CockroachTimeBuilder";
	constructor(name, withTimezone, precision) {
		super(name, "string time", "CockroachTime");
		this.withTimezone = withTimezone;
		this.precision = precision;
		this.config.withTimezone = withTimezone;
		this.config.precision = precision;
	}
	/** @internal */
	build(table) {
		return new CockroachTime(table, this.config);
	}
};
var CockroachTime = class extends require_cockroach_core_columns_common.CockroachColumn {
	static [__entity_ts.entityKind] = "CockroachTime";
	withTimezone;
	precision;
	constructor(table, config) {
		super(table, config);
		this.withTimezone = config.withTimezone;
		this.precision = config.precision;
	}
	getSQLType() {
		const precision = this.precision === void 0 ? "" : `(${this.precision})`;
		return `time${this.withTimezone ? "tz" : ""}${precision}`;
	}
};
function time(a, b = {}) {
	const { name, config } = (0, __utils_ts.getColumnNameAndConfig)(a, b);
	return new CockroachTimeBuilder(name, config.withTimezone ?? false, config.precision);
}

//#endregion
exports.CockroachTime = CockroachTime;
exports.CockroachTimeBuilder = CockroachTimeBuilder;
exports.time = time;
//# sourceMappingURL=time.cjs.map