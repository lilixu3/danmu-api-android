import { MsSqlColumn, MsSqlColumnBuilder } from "./common.js";
import { entityKind } from "../../entity.js";
import { getColumnNameAndConfig } from "../../utils.js";

//#region src/mssql-core/columns/varbinary.ts
var MsSqlVarBinaryBuilder = class extends MsSqlColumnBuilder {
	static [entityKind] = "MsSqlVarBinaryBuilder";
	/** @internal */
	constructor(name, config) {
		super(name, "object buffer", "MsSqlVarBinary");
		this.config.length = typeof config?.length === "number" ? config.length : config?.length === "max" ? 2147483647 : 1;
		this.config.rawLength = config?.length;
	}
	/** @internal */
	build(table) {
		return new MsSqlVarBinary(table, this.config);
	}
};
var MsSqlVarBinary = class extends MsSqlColumn {
	static [entityKind] = "MsSqlVarBinary";
	getSQLType() {
		return this.config.rawLength === void 0 ? `varbinary` : `varbinary(${this.config.rawLength})`;
	}
};
function varbinary(a, b) {
	const { name, config } = getColumnNameAndConfig(a, b);
	return new MsSqlVarBinaryBuilder(name, config);
}

//#endregion
export { MsSqlVarBinary, MsSqlVarBinaryBuilder, varbinary };
//# sourceMappingURL=varbinary.js.map