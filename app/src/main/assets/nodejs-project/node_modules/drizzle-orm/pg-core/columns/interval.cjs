Object.defineProperty(exports, Symbol.toStringTag, { value: 'Module' });
const require_runtime = require('../../_virtual/_rolldown/runtime.cjs');
const require_pg_core_columns_common = require('./common.cjs');
let __entity_ts = require("../../entity.cjs");
let __utils_ts = require("../../utils.cjs");

//#region src/pg-core/columns/interval.ts
var PgIntervalBuilder = class extends require_pg_core_columns_common.PgColumnBuilder {
	static [__entity_ts.entityKind] = "PgIntervalBuilder";
	constructor(name, intervalConfig) {
		super(name, "string interval", "PgInterval");
		this.config.intervalConfig = intervalConfig;
	}
	/** @internal */
	build(table) {
		return new PgInterval(table, this.config);
	}
};
var PgInterval = class extends require_pg_core_columns_common.PgColumn {
	static [__entity_ts.entityKind] = "PgInterval";
	/** @internal */
	codec = "interval";
	fields;
	precision;
	constructor(table, config) {
		super(table, config);
		this.fields = config.intervalConfig.fields;
		this.precision = config.intervalConfig.precision;
	}
	getSQLType() {
		return `interval${this.fields ? ` ${this.fields}` : ""}${this.precision ? `(${this.precision})` : ""}`;
	}
};
function interval(a, b = {}) {
	const { name, config } = (0, __utils_ts.getColumnNameAndConfig)(a, b);
	return new PgIntervalBuilder(name, config);
}

//#endregion
exports.PgInterval = PgInterval;
exports.PgIntervalBuilder = PgIntervalBuilder;
exports.interval = interval;
//# sourceMappingURL=interval.cjs.map