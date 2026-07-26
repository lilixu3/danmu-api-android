Object.defineProperty(exports, Symbol.toStringTag, { value: 'Module' });
const require_runtime = require('../../_virtual/_rolldown/runtime.cjs');
const require_mssql_core_columns_common = require('./common.cjs');
let __entity_ts = require("../../entity.cjs");
let __utils_ts = require("../../utils.cjs");

//#region src/mssql-core/columns/char.ts
var MsSqlCharBuilder = class extends require_mssql_core_columns_common.MsSqlColumnBuilder {
	static [__entity_ts.entityKind] = "MsSqlCharBuilder";
	/** @internal */
	constructor(name, config) {
		super(name, config.enum?.length ? "string enum" : "string", "MsSqlChar");
		this.config.length = config.length ?? 1;
		this.config.enum = config.enum;
		this.config.nonUnicode = config.nonUnicode;
	}
	/** @internal */
	build(table) {
		return new MsSqlChar(table, this.config);
	}
};
var MsSqlChar = class extends require_mssql_core_columns_common.MsSqlColumn {
	static [__entity_ts.entityKind] = "MsSqlChar";
	enumValues = this.config.enum;
	nonUnicode = this.config.nonUnicode;
	getSQLType() {
		return this.length === void 0 ? this.nonUnicode ? `nchar` : `char` : this.nonUnicode ? `nchar(${this.length})` : `char(${this.length})`;
	}
};
function char(a, b) {
	const { name, config } = (0, __utils_ts.getColumnNameAndConfig)(a, b);
	return new MsSqlCharBuilder(name, {
		...config,
		nonUnicode: false
	});
}
function nchar(a, b) {
	const { name, config } = (0, __utils_ts.getColumnNameAndConfig)(a, b);
	return new MsSqlCharBuilder(name, {
		...config,
		nonUnicode: true
	});
}

//#endregion
exports.MsSqlChar = MsSqlChar;
exports.MsSqlCharBuilder = MsSqlCharBuilder;
exports.char = char;
exports.nchar = nchar;
//# sourceMappingURL=char.cjs.map