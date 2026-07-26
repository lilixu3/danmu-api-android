Object.defineProperty(exports, Symbol.toStringTag, { value: 'Module' });
const require_runtime = require('../../_virtual/_rolldown/runtime.cjs');
const require_singlestore_core_columns_common = require('./common.cjs');
let __entity_ts = require("../../entity.cjs");
let __utils_ts = require("../../utils.cjs");

//#region src/singlestore-core/columns/text.ts
var SingleStoreTextBuilder = class extends require_singlestore_core_columns_common.SingleStoreColumnBuilder {
	static [__entity_ts.entityKind] = "SingleStoreTextBuilder";
	constructor(name, textType, config) {
		super(name, config.enum?.length ? "string enum" : "string", "SingleStoreText");
		this.config.textType = textType;
		this.config.enumValues = config.enum;
		switch (textType) {
			case "tinytext":
				this.config.length = 255;
				break;
			case "text":
				this.config.length = 65535;
				break;
			case "mediumtext":
				this.config.length = 16777215;
				break;
			case "longtext":
				this.config.length = 4294967295;
				break;
		}
	}
	/** @internal */
	build(table) {
		return new SingleStoreText(table, this.config);
	}
};
var SingleStoreText = class extends require_singlestore_core_columns_common.SingleStoreColumn {
	static [__entity_ts.entityKind] = "SingleStoreText";
	textType = this.config.textType;
	enumValues = this.config.enumValues;
	getSQLType() {
		return this.textType;
	}
};
function text(a, b = {}) {
	const { name, config } = (0, __utils_ts.getColumnNameAndConfig)(a, b);
	return new SingleStoreTextBuilder(name, "text", config);
}
function tinytext(a, b = {}) {
	const { name, config } = (0, __utils_ts.getColumnNameAndConfig)(a, b);
	return new SingleStoreTextBuilder(name, "tinytext", config);
}
function mediumtext(a, b = {}) {
	const { name, config } = (0, __utils_ts.getColumnNameAndConfig)(a, b);
	return new SingleStoreTextBuilder(name, "mediumtext", config);
}
function longtext(a, b = {}) {
	const { name, config } = (0, __utils_ts.getColumnNameAndConfig)(a, b);
	return new SingleStoreTextBuilder(name, "longtext", config);
}

//#endregion
exports.SingleStoreText = SingleStoreText;
exports.SingleStoreTextBuilder = SingleStoreTextBuilder;
exports.longtext = longtext;
exports.mediumtext = mediumtext;
exports.text = text;
exports.tinytext = tinytext;
//# sourceMappingURL=text.cjs.map