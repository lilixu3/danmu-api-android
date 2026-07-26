import { SingleStoreColumn, SingleStoreColumnBuilder } from "./common.js";
import { entityKind } from "../../entity.js";
import { getColumnNameAndConfig } from "../../utils.js";

//#region src/singlestore-core/columns/text.ts
var SingleStoreTextBuilder = class extends SingleStoreColumnBuilder {
	static [entityKind] = "SingleStoreTextBuilder";
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
var SingleStoreText = class extends SingleStoreColumn {
	static [entityKind] = "SingleStoreText";
	textType = this.config.textType;
	enumValues = this.config.enumValues;
	getSQLType() {
		return this.textType;
	}
};
function text(a, b = {}) {
	const { name, config } = getColumnNameAndConfig(a, b);
	return new SingleStoreTextBuilder(name, "text", config);
}
function tinytext(a, b = {}) {
	const { name, config } = getColumnNameAndConfig(a, b);
	return new SingleStoreTextBuilder(name, "tinytext", config);
}
function mediumtext(a, b = {}) {
	const { name, config } = getColumnNameAndConfig(a, b);
	return new SingleStoreTextBuilder(name, "mediumtext", config);
}
function longtext(a, b = {}) {
	const { name, config } = getColumnNameAndConfig(a, b);
	return new SingleStoreTextBuilder(name, "longtext", config);
}

//#endregion
export { SingleStoreText, SingleStoreTextBuilder, longtext, mediumtext, text, tinytext };
//# sourceMappingURL=text.js.map