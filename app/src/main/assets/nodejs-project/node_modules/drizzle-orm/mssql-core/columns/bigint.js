import { MsSqlColumnBuilderWithIdentity, MsSqlColumnWithIdentity } from "./common.js";
import { entityKind } from "../../entity.js";
import { getColumnNameAndConfig } from "../../utils.js";

//#region src/mssql-core/columns/bigint.ts
var MsSqlBigIntBuilder = class extends MsSqlColumnBuilderWithIdentity {
	static [entityKind] = "MsSqlBigIntBuilder";
	constructor(name, config) {
		const { mode } = config;
		super(name, mode === "string" ? "string int64" : mode === "number" ? "number int53" : "bigint int64", mode === "string" ? "MsSqlBigIntString" : mode === "number" ? "MsSqlBigIntNumber" : "MsSqlBigInt");
		this.config.mode = mode;
	}
	/** @internal */
	build(table) {
		return new MsSqlBigInt(table, this.config);
	}
};
var MsSqlBigInt = class extends MsSqlColumnWithIdentity {
	static [entityKind] = "MsSqlBigInt";
	mode = this.config.mode;
	getSQLType() {
		return `bigint`;
	}
	constructor(table, config) {
		super(table, config);
		this.mode = config.mode;
	}
	mapFromDriverValue = (value) => {
		return this.mode === "string" ? value.toString() : this.mode === "number" ? Number(value) : BigInt(value);
	};
};
function bigint(a, b) {
	const { name, config } = getColumnNameAndConfig(a, b);
	return new MsSqlBigIntBuilder(name, config);
}

//#endregion
export { MsSqlBigInt, MsSqlBigIntBuilder, bigint };
//# sourceMappingURL=bigint.js.map