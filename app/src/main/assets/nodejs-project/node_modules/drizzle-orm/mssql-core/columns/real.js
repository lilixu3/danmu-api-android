import { MsSqlColumnBuilderWithIdentity, MsSqlColumnWithIdentity } from "./common.js";
import { entityKind } from "../../entity.js";

//#region src/mssql-core/columns/real.ts
var MsSqlRealBuilder = class extends MsSqlColumnBuilderWithIdentity {
	static [entityKind] = "MsSqlRealBuilder";
	constructor(name) {
		super(name, "number float", "MsSqlReal");
	}
	/** @internal */
	build(table) {
		return new MsSqlReal(table, this.config);
	}
};
var MsSqlReal = class extends MsSqlColumnWithIdentity {
	static [entityKind] = "MsSqlReal";
	getSQLType() {
		return "real";
	}
	mapFromDriverValue = (value) => {
		return parseFloat(value.toPrecision(7));
	};
};
function real(name) {
	return new MsSqlRealBuilder(name ?? "");
}

//#endregion
export { MsSqlReal, MsSqlRealBuilder, real };
//# sourceMappingURL=real.js.map