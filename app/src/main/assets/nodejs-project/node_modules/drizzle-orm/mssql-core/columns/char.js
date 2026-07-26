import { MsSqlColumn, MsSqlColumnBuilder } from "./common.js";
import { entityKind } from "../../entity.js";
import { getColumnNameAndConfig } from "../../utils.js";

//#region src/mssql-core/columns/char.ts
var MsSqlCharBuilder = class extends MsSqlColumnBuilder {
	static [entityKind] = "MsSqlCharBuilder";
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
var MsSqlChar = class extends MsSqlColumn {
	static [entityKind] = "MsSqlChar";
	enumValues = this.config.enum;
	nonUnicode = this.config.nonUnicode;
	getSQLType() {
		return this.length === void 0 ? this.nonUnicode ? `nchar` : `char` : this.nonUnicode ? `nchar(${this.length})` : `char(${this.length})`;
	}
};
function char(a, b) {
	const { name, config } = getColumnNameAndConfig(a, b);
	return new MsSqlCharBuilder(name, {
		...config,
		nonUnicode: false
	});
}
function nchar(a, b) {
	const { name, config } = getColumnNameAndConfig(a, b);
	return new MsSqlCharBuilder(name, {
		...config,
		nonUnicode: true
	});
}

//#endregion
export { MsSqlChar, MsSqlCharBuilder, char, nchar };
//# sourceMappingURL=char.js.map