import { CockroachColumn } from "./common.js";
import { CockroachIntColumnBaseBuilder } from "./int.common.js";
import { entityKind } from "../../entity.js";
import { getColumnNameAndConfig } from "../../utils.js";

//#region src/cockroach-core/columns/bigint.ts
var CockroachBigInt53Builder = class extends CockroachIntColumnBaseBuilder {
	static [entityKind] = "CockroachBigInt53Builder";
	constructor(name) {
		super(name, "number int53", "CockroachBigInt53");
	}
	/** @internal */
	build(table) {
		return new CockroachBigInt53(table, this.config);
	}
};
var CockroachBigInt53 = class extends CockroachColumn {
	static [entityKind] = "CockroachBigInt53";
	getSQLType() {
		return "int8";
	}
	mapFromDriverValue = (value) => {
		if (typeof value === "number") return value;
		return Number(value);
	};
};
var CockroachBigInt64Builder = class extends CockroachIntColumnBaseBuilder {
	static [entityKind] = "CockroachBigInt64Builder";
	constructor(name) {
		super(name, "bigint int64", "CockroachBigInt64");
	}
	/** @internal */
	build(table) {
		return new CockroachBigInt64(table, this.config);
	}
};
var CockroachBigInt64 = class extends CockroachColumn {
	static [entityKind] = "CockroachBigInt64";
	getSQLType() {
		return "int8";
	}
	mapFromDriverValue = (value) => {
		return BigInt(value);
	};
};
function bigint(a, b) {
	const { name, config } = getColumnNameAndConfig(a, b);
	if (config.mode === "number") return new CockroachBigInt53Builder(name);
	return new CockroachBigInt64Builder(name);
}
function int8(a, b) {
	const { name, config } = getColumnNameAndConfig(a, b);
	if (config.mode === "number") return new CockroachBigInt53Builder(name);
	return new CockroachBigInt64Builder(name);
}

//#endregion
export { CockroachBigInt53, CockroachBigInt53Builder, CockroachBigInt64, CockroachBigInt64Builder, bigint, int8 };
//# sourceMappingURL=bigint.js.map