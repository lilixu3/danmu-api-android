import { MsSqlColumnBuilderWithIdentity, MsSqlColumnWithIdentity } from "./common.js";
import { entityKind } from "../../entity.js";

//#region src/mssql-core/columns/smallint.ts
var MsSqlSmallIntBuilder = class extends MsSqlColumnBuilderWithIdentity {
	static [entityKind] = "MsSqlSmallIntBuilder";
	constructor(name) {
		super(name, "number int16", "MsSqlSmallInt");
	}
	/** @internal */
	build(table) {
		return new MsSqlSmallInt(table, this.config);
	}
};
var MsSqlSmallInt = class extends MsSqlColumnWithIdentity {
	static [entityKind] = "MsSqlSmallInt";
	getSQLType() {
		return `smallint`;
	}
	mapFromDriverValue = (value) => {
		if (typeof value === "string") return Number(value);
		return value;
	};
};
function smallint(name) {
	return new MsSqlSmallIntBuilder(name ?? "");
}

//#endregion
export { MsSqlSmallInt, MsSqlSmallIntBuilder, smallint };
//# sourceMappingURL=smallint.js.map