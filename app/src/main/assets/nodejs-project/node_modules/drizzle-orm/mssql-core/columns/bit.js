import { MsSqlColumnBuilderWithIdentity, MsSqlColumnWithIdentity } from "./common.js";
import { entityKind } from "../../entity.js";

//#region src/mssql-core/columns/bit.ts
var MsSqlBitBuilder = class extends MsSqlColumnBuilderWithIdentity {
	static [entityKind] = "MsSqlBitBuilder";
	constructor(name) {
		super(name, "boolean", "MsSqlBit");
	}
	/** @internal */
	build(table) {
		return new MsSqlBit(table, this.config);
	}
};
var MsSqlBit = class extends MsSqlColumnWithIdentity {
	static [entityKind] = "MsSqlBit";
	getSQLType() {
		return `bit`;
	}
	mapFromDriverValue = Boolean;
};
function bit(name) {
	return new MsSqlBitBuilder(name ?? "");
}

//#endregion
export { MsSqlBit, MsSqlBitBuilder, bit };
//# sourceMappingURL=bit.js.map