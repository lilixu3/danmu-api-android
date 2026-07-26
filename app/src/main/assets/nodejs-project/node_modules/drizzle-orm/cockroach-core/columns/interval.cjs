Object.defineProperty(exports, Symbol.toStringTag, { value: 'Module' });
const require_runtime = require('../../_virtual/_rolldown/runtime.cjs');
const require_cockroach_core_columns_common = require('./common.cjs');
let __entity_ts = require("../../entity.cjs");
let __utils_ts = require("../../utils.cjs");

//#region src/cockroach-core/columns/interval.ts
var CockroachIntervalBuilder = class extends require_cockroach_core_columns_common.CockroachColumnWithArrayBuilder {
	static [__entity_ts.entityKind] = "CockroachIntervalBuilder";
	constructor(name, intervalConfig) {
		super(name, "string interval", "CockroachInterval");
		this.config.intervalConfig = intervalConfig;
	}
	/** @internal */
	build(table) {
		return new CockroachInterval(table, this.config);
	}
};
var CockroachInterval = class extends require_cockroach_core_columns_common.CockroachColumn {
	static [__entity_ts.entityKind] = "CockroachInterval";
	fields = this.config.intervalConfig.fields;
	precision = this.config.intervalConfig.precision;
	getSQLType() {
		return `interval${this.fields ? ` ${this.fields}` : ""}${this.precision ? `(${this.precision})` : ""}`;
	}
};
function interval(a, b = {}) {
	const { name, config } = (0, __utils_ts.getColumnNameAndConfig)(a, b);
	return new CockroachIntervalBuilder(name, config);
}

//#endregion
exports.CockroachInterval = CockroachInterval;
exports.CockroachIntervalBuilder = CockroachIntervalBuilder;
exports.interval = interval;
//# sourceMappingURL=interval.cjs.map