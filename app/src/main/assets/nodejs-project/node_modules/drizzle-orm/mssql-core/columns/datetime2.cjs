Object.defineProperty(exports, Symbol.toStringTag, { value: 'Module' });
const require_runtime = require('../../_virtual/_rolldown/runtime.cjs');
const require_mssql_core_columns_common = require('./common.cjs');
const require_mssql_core_columns_date_common = require('./date.common.cjs');
let __entity_ts = require("../../entity.cjs");
let __utils_ts = require("../../utils.cjs");

//#region src/mssql-core/columns/datetime2.ts
var MsSqlDateTime2Builder = class extends require_mssql_core_columns_date_common.MsSqlDateColumnBaseBuilder {
	static [__entity_ts.entityKind] = "MsSqlDateTime2Builder";
	constructor(name, config) {
		super(name, "object date", "MsSqlDateTime2");
		this.config.precision = config?.precision;
	}
	/** @internal */
	build(table) {
		return new MsSqlDateTime2(table, this.config);
	}
};
var MsSqlDateTime2 = class extends require_mssql_core_columns_common.MsSqlColumn {
	static [__entity_ts.entityKind] = "MsSqlDateTime2";
	precision;
	constructor(table, config) {
		super(table, config);
		this.precision = config.precision;
	}
	getSQLType() {
		return `datetime2${this.precision === void 0 ? "" : `(${this.precision})`}`;
	}
};
var MsSqlDateTime2StringBuilder = class extends require_mssql_core_columns_date_common.MsSqlDateColumnBaseBuilder {
	static [__entity_ts.entityKind] = "MsSqlDateTime2StringBuilder";
	constructor(name, config) {
		super(name, "string datetime", "MsSqlDateTime2String");
		this.config.precision = config?.precision;
	}
	/** @internal */
	build(table) {
		return new MsSqlDateTime2String(table, this.config);
	}
};
var MsSqlDateTime2String = class extends require_mssql_core_columns_common.MsSqlColumn {
	static [__entity_ts.entityKind] = "MsSqlDateTime2String";
	precision;
	constructor(table, config) {
		super(table, config);
		this.precision = config.precision;
	}
	getSQLType() {
		return `datetime2${this.precision === void 0 ? "" : `(${this.precision})`}`;
	}
	mapFromDriverValue = (value) => {
		return typeof value === "string" ? value : value?.toISOString() ?? null;
	};
};
function datetime2(a, b) {
	const { name, config } = (0, __utils_ts.getColumnNameAndConfig)(a, b);
	if (config?.mode === "string") return new MsSqlDateTime2StringBuilder(name, config);
	return new MsSqlDateTime2Builder(name, config);
}

//#endregion
exports.MsSqlDateTime2 = MsSqlDateTime2;
exports.MsSqlDateTime2Builder = MsSqlDateTime2Builder;
exports.MsSqlDateTime2String = MsSqlDateTime2String;
exports.MsSqlDateTime2StringBuilder = MsSqlDateTime2StringBuilder;
exports.datetime2 = datetime2;
//# sourceMappingURL=datetime2.cjs.map