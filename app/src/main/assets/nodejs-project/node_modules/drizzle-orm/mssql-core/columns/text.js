import { MsSqlColumn, MsSqlColumnBuilder } from "./common.js";
import { entityKind } from "../../entity.js";
import { getColumnNameAndConfig } from "../../utils.js";

//#region src/mssql-core/columns/text.ts
var MsSqlTextBuilder = class extends MsSqlColumnBuilder {
	static [entityKind] = "MsSqlTextBuilder";
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
var MsSqlText = class extends MsSqlColumn {
	static [entityKind] = "MsSqlText";
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
	const { name, config } = getColumnNameAndConfig(a, b);
	return new MsSqlTextBuilder(name, {
		...config,
		nonUnicode: false
	});
}
function ntext(a, b) {
	const { name, config } = getColumnNameAndConfig(a, b);
	return new MsSqlTextBuilder(name, {
		...config,
		nonUnicode: true
	});
}

//#endregion
export { MsSqlText, MsSqlTextBuilder, ntext, text };
//# sourceMappingURL=text.js.map