Object.defineProperty(exports, Symbol.toStringTag, { value: 'Module' });
const require_runtime = require('../../_virtual/_rolldown/runtime.cjs');
const require_mssql_core_columns_common = require('./common.cjs');
let __entity_ts = require("../../entity.cjs");
let __utils_ts = require("../../utils.cjs");

//#region src/mssql-core/columns/varchar.ts
var MsSqlVarCharBuilder = class extends require_mssql_core_columns_common.MsSqlColumnBuilder {
	static [__entity_ts.entityKind] = "MsSqlVarCharBuilder";
	/** @internal */
	constructor(name, config) {
		super(name, config.enum?.length ? "string enum" : "string", "MsSqlVarChar");
		this.config.length = typeof config?.length === "number" ? config.length : config?.length === "max" ? 2147483647 : 1;
		this.config.rawLength = config?.length;
		this.config.enum = config.enum;
		this.config.nonUnicode = config.nonUnicode;
	}
	/** @internal */
	build(table) {
		return new MsSqlVarChar(table, this.config);
	}
};
var MsSqlVarChar = class extends require_mssql_core_columns_common.MsSqlColumn {
	static [__entity_ts.entityKind] = "MsSqlVarChar";
	enumValues = this.config.enum;
	nonUnicode = this.config.nonUnicode;
	getSQLType() {
		return this.config.rawLength === void 0 ? this.nonUnicode ? `nvarchar` : `varchar` : this.nonUnicode ? `nvarchar(${this.config.rawLength})` : `varchar(${this.config.rawLength})`;
	}
};
var MsSqlVarCharJsonBuilder = class extends require_mssql_core_columns_common.MsSqlColumnBuilder {
	static [__entity_ts.entityKind] = "MsSqlVarCharJsonBuilder";
	/** @internal */
	constructor(name, config) {
		super(name, "object json", "MsSqlNVarCharJson");
		this.config.length = typeof config?.length === "number" ? config.length : config?.length === "max" ? 2147483647 : 1;
		this.config.rawLength = config?.length;
		this.config.nonUnicode = true;
	}
	/** @internal */
	build(table) {
		return new MsSqlVarCharJson(table, this.config);
	}
};
var MsSqlVarCharJson = class extends require_mssql_core_columns_common.MsSqlColumn {
	static [__entity_ts.entityKind] = "MsSqlVarCharJson";
	getSQLType() {
		return this.config.rawLength === void 0 ? `nvarchar` : `nvarchar(${this.config.rawLength})`;
	}
	mapFromDriverValue = (value) => {
		return JSON.parse(value);
	};
	mapToDriverValue = (value) => {
		return JSON.stringify(value);
	};
};
function varchar(a, b) {
	const { name, config } = (0, __utils_ts.getColumnNameAndConfig)(a, b);
	return new MsSqlVarCharBuilder(name, {
		...config,
		mode: "text",
		nonUnicode: false
	});
}
function nvarchar(a, b) {
	const { name, config } = (0, __utils_ts.getColumnNameAndConfig)(a, b);
	if (config?.mode === "json") return new MsSqlVarCharJsonBuilder(name, { length: config.length });
	return new MsSqlVarCharBuilder(name, {
		length: config?.length,
		enum: config?.enum,
		nonUnicode: true
	});
}

//#endregion
exports.MsSqlVarChar = MsSqlVarChar;
exports.MsSqlVarCharBuilder = MsSqlVarCharBuilder;
exports.MsSqlVarCharJson = MsSqlVarCharJson;
exports.MsSqlVarCharJsonBuilder = MsSqlVarCharJsonBuilder;
exports.nvarchar = nvarchar;
exports.varchar = varchar;
//# sourceMappingURL=varchar.cjs.map