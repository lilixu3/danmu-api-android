Object.defineProperty(exports, Symbol.toStringTag, { value: 'Module' });
const require_runtime = require('../../_virtual/_rolldown/runtime.cjs');
const require_mssql_core_columns_common = require('./common.cjs');
let __entity_ts = require("../../entity.cjs");
let __utils_ts = require("../../utils.cjs");

//#region src/mssql-core/columns/time.ts
var MsSqlTimeStringBuilder = class extends require_mssql_core_columns_common.MsSqlColumnBuilder {
	static [__entity_ts.entityKind] = "MsSqlTimeBuilder";
	constructor(name, config) {
		super(name, "string time", "MsSqlTime");
		this.config.precision = config?.precision;
	}
	/** @internal */
	build(table) {
		return new MsSqlTimeString(table, this.config);
	}
};
var MsSqlTimeString = class extends require_mssql_core_columns_common.MsSqlColumn {
	static [__entity_ts.entityKind] = "MsSqlTime";
	fsp = this.config.precision;
	getSQLType() {
		return `time${this.fsp === void 0 ? "" : `(${this.fsp})`}`;
	}
	mapFromDriverValue = (value) => {
		return typeof value === "string" ? value : value?.toISOString().split("T")[1]?.split("Z")[0] ?? null;
	};
};
var MsSqlTimeBuilder = class extends require_mssql_core_columns_common.MsSqlColumnBuilder {
	static [__entity_ts.entityKind] = "MsSqlTimeBuilder";
	constructor(name, config) {
		super(name, "object date", "MsSqlTime");
		this.config.precision = config?.precision;
	}
	/** @internal */
	build(table) {
		return new MsSqlTime(table, this.config);
	}
};
var MsSqlTime = class extends require_mssql_core_columns_common.MsSqlColumn {
	static [__entity_ts.entityKind] = "MsSqlTime";
	fsp = this.config.precision;
	getSQLType() {
		return `time${this.fsp === void 0 ? "" : `(${this.fsp})`}`;
	}
};
function time(a, b) {
	const { name, config } = (0, __utils_ts.getColumnNameAndConfig)(a, b);
	if (config?.mode === "string") return new MsSqlTimeStringBuilder(name, config);
	return new MsSqlTimeBuilder(name, config);
}

//#endregion
exports.MsSqlTime = MsSqlTime;
exports.MsSqlTimeBuilder = MsSqlTimeBuilder;
exports.MsSqlTimeString = MsSqlTimeString;
exports.MsSqlTimeStringBuilder = MsSqlTimeStringBuilder;
exports.time = time;
//# sourceMappingURL=time.cjs.map