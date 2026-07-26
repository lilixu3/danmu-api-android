import { MsSqlColumnBuilderWithIdentity, MsSqlColumnWithIdentity } from "./common.js";
import { entityKind } from "../../entity.js";

//#region src/mssql-core/columns/tinyint.ts
var MsSqlTinyIntBuilder = class extends MsSqlColumnBuilderWithIdentity {
	static [entityKind] = "MsSqlTinyIntBuilder";
	constructor(name) {
		super(name, "number uint8", "MsSqlTinyInt");
	}
	/** @internal */
	build(table) {
		return new MsSqlTinyInt(table, this.config);
	}
};
var MsSqlTinyInt = class extends MsSqlColumnWithIdentity {
	static [entityKind] = "MsSqlTinyInt";
	getSQLType() {
		return `tinyint`;
	}
	mapFromDriverValue = (value) => {
		if (typeof value === "string") return Number(value);
		return value;
	};
};
function tinyint(name) {
	return new MsSqlTinyIntBuilder(name ?? "");
}

//#endregion
export { MsSqlTinyInt, MsSqlTinyIntBuilder, tinyint };
//# sourceMappingURL=tinyint.js.map