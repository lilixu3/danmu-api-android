Object.defineProperty(exports, Symbol.toStringTag, { value: 'Module' });
const require_runtime = require('../../_virtual/_rolldown/runtime.cjs');
const require_mssql_core_columns_common = require('./common.cjs');
let __entity_ts = require("../../entity.cjs");
let __utils_ts = require("../../utils.cjs");

//#region src/mssql-core/columns/text.ts
var MsSqlTextBuilder = class extends require_mssql_core_columns_common.MsSqlColumnBuilder {
	static [__entity_ts.entityKind] = "MsSqlTextBuilder";
	constructor(name, config) {
		super(name, config.enum?.length ? "string enum" : "string", "MsSqlText");
		this.config.enumValues = config.enum;
		this.config.nonUnicode = config.nonUnicode;
	}
	/** @internal */
	build(table) {
		return new MsSqlText(table, this.config);
	}
};
var MsSqlText = class extends require_mssql_core_columns_common.MsSqlColumn {
	static [__entity_ts.entityKind] = "MsSqlText";
	enumValues = this.config.enumValues;
	nonUnicode = this.config.nonUnicode;
	constructor(table, config) {
		super(table, config);
	}
	getSQLType() {
		return `${this.nonUnicode ? "n" : ""}text`;
	}
};
function text(a, b) {
	const { name, config } = (0, __utils_ts.getColumnNameAndConfig)(a, b);
	return new MsSqlTextBuilder(name, {
		...config,
		nonUnicode: false
	});
}
function ntext(a, b) {
	const { name, config } = (0, __utils_ts.getColumnNameAndConfig)(a, b);
	return new MsSqlTextBuilder(name, {
		...config,
		nonUnicode: true
	});
}

//#endregion
exports.MsSqlText = MsSqlText;
exports.MsSqlTextBuilder = MsSqlTextBuilder;
exports.ntext = ntext;
exports.text = text;
//# sourceMappingURL=text.cjs.map