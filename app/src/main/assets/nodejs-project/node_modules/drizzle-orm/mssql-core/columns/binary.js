import { MsSqlColumn, MsSqlColumnBuilder } from "./common.js";
import { entityKind } from "../../entity.js";
import { getColumnNameAndConfig } from "../../utils.js";

//#region src/mssql-core/columns/binary.ts
var MsSqlBinaryBuilder = class extends MsSqlColumnBuilder {
	static [entityKind] = "MsSqlBinaryBuilder";
	constructor(name, length) {
		super(name, "object buffer", "MsSqlBinary");
		this.config.length = length ?? 1;
		this.config.setLength = length !== void 0;
	}
	/** @internal */
	build(table) {
		return new MsSqlBinary(table, this.config);
	}
};
var MsSqlBinary = class extends MsSqlColumn {
	static [entityKind] = "MsSqlBinary";
	getSQLType() {
		return this.config.setLength ? `binary(${this.length})` : `binary`;
	}
};
function binary(a, b = {}) {
	const { name, config } = getColumnNameAndConfig(a, b);
	return new MsSqlBinaryBuilder(name, config.length);
}

//#endregion
export { MsSqlBinary, MsSqlBinaryBuilder, binary };
//# sourceMappingURL=binary.js.map