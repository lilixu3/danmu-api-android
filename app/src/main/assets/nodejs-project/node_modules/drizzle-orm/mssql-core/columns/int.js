import { MsSqlColumnBuilderWithIdentity, MsSqlColumnWithIdentity } from "./common.js";
import { entityKind } from "../../entity.js";

//#region src/mssql-core/columns/int.ts
var MsSqlIntBuilder = class extends MsSqlColumnBuilderWithIdentity {
	static [entityKind] = "MsSqlIntBuilder";
	constructor(name) {
		super(name, "number int32", "MsSqlInt");
	}
	/** @internal */
	build(table) {
		return new MsSqlInt(table, this.config);
	}
};
var MsSqlInt = class extends MsSqlColumnWithIdentity {
	static [entityKind] = "MsSqlInt";
	getSQLType() {
		return `int`;
	}
};
function int(name) {
	return new MsSqlIntBuilder(name ?? "");
}

//#endregion
export { MsSqlInt, MsSqlIntBuilder, int };
//# sourceMappingURL=int.js.map