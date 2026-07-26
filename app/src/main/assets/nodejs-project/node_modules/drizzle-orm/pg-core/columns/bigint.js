import { PgColumn } from "./common.js";
import { PgIntColumnBuilder } from "./int.common.js";
import { entityKind } from "../../entity.js";
import { getColumnNameAndConfig } from "../../utils.js";

//#region src/pg-core/columns/bigint.ts
var PgBigInt53Builder = class extends PgIntColumnBuilder {
	static [entityKind] = "PgBigInt53Builder";
	constructor(name) {
		super(name, "number int53", "PgBigInt53");
	}
	/** @internal */
	build(table) {
		return new PgBigInt53(table, this.config);
	}
};
var PgBigInt53 = class extends PgColumn {
	static [entityKind] = "PgBigInt53";
	/** @internal */
	codec = "bigint:number";
	getSQLType() {
		return "bigint";
	}
};
var PgBigInt64Builder = class extends PgIntColumnBuilder {
	static [entityKind] = "PgBigInt64Builder";
	constructor(name) {
		super(name, "bigint int64", "PgBigInt64");
	}
	/** @internal */
	build(table) {
		return new PgBigInt64(table, this.config);
	}
};
var PgBigInt64 = class extends PgColumn {
	static [entityKind] = "PgBigInt64";
	/** @internal */
	codec = "bigint";
	getSQLType() {
		return "bigint";
	}
};
var PgBigIntStringBuilder = class extends PgIntColumnBuilder {
	static [entityKind] = "PgBigIntStringBuilder";
	constructor(name) {
		super(name, "string int64", "PgBigIntString");
	}
	/** @internal */
	build(table) {
		return new PgBigIntString(table, this.config);
	}
};
var PgBigIntString = class extends PgColumn {
	static [entityKind] = "PgBigIntString";
	/** @internal */
	codec = "bigint:string";
	getSQLType() {
		return "bigint";
	}
};
function bigint(a, b) {
	const { name, config } = getColumnNameAndConfig(a, b);
	if (config.mode === "number") return new PgBigInt53Builder(name);
	if (config.mode === "string") return new PgBigIntStringBuilder(name);
	return new PgBigInt64Builder(name);
}

//#endregion
export { PgBigInt53, PgBigInt53Builder, PgBigInt64, PgBigInt64Builder, PgBigIntString, PgBigIntStringBuilder, bigint };
//# sourceMappingURL=bigint.js.map