import { SingleStoreColumnBuilderWithAutoIncrement, SingleStoreColumnWithAutoIncrement } from "./common.js";
import { entityKind } from "../../entity.js";
import { getColumnNameAndConfig } from "../../utils.js";

//#region src/singlestore-core/columns/bigint.ts
var SingleStoreBigInt53Builder = class extends SingleStoreColumnBuilderWithAutoIncrement {
	static [entityKind] = "SingleStoreBigInt53Builder";
	constructor(name, unsigned = false) {
		super(name, unsigned ? "number uint53" : "number int53", "SingleStoreBigInt53");
		this.config.unsigned = unsigned;
	}
	/** @internal */
	build(table) {
		return new SingleStoreBigInt53(table, this.config);
	}
};
var SingleStoreBigInt53 = class extends SingleStoreColumnWithAutoIncrement {
	static [entityKind] = "SingleStoreBigInt53";
	getSQLType() {
		return `bigint${this.config.unsigned ? " unsigned" : ""}`;
	}
	mapFromDriverValue = (value) => {
		if (typeof value === "number") return value;
		return Number(value);
	};
};
var SingleStoreBigInt64Builder = class extends SingleStoreColumnBuilderWithAutoIncrement {
	static [entityKind] = "SingleStoreBigInt64Builder";
	constructor(name, unsigned = false) {
		super(name, unsigned ? "bigint uint64" : "bigint int64", "SingleStoreBigInt64");
		this.config.unsigned = unsigned;
	}
	/** @internal */
	build(table) {
		return new SingleStoreBigInt64(table, this.config);
	}
};
var SingleStoreBigInt64 = class extends SingleStoreColumnWithAutoIncrement {
	static [entityKind] = "SingleStoreBigInt64";
	getSQLType() {
		return `bigint${this.config.unsigned ? " unsigned" : ""}`;
	}
	mapFromDriverValue = (value) => {
		return BigInt(value);
	};
};
var SingleStoreBigIntStringBuilder = class extends SingleStoreColumnBuilderWithAutoIncrement {
	static [entityKind] = "SingleStoreBigIntStringBuilder";
	constructor(name, unsigned = false) {
		super(name, unsigned ? "string uint64" : "string int64", "SingleStoreBigIntString");
		this.config.unsigned = unsigned;
	}
	/** @internal */
	build(table) {
		return new SingleStoreBigIntString(table, this.config);
	}
};
var SingleStoreBigIntString = class extends SingleStoreColumnWithAutoIncrement {
	static [entityKind] = "SingleStoreBigIntString";
	getSQLType() {
		return `bigint${this.config.unsigned ? " unsigned" : ""}`;
	}
};
function bigint(a, b) {
	const { name, config } = getColumnNameAndConfig(a, b);
	if (config.mode === "number") return new SingleStoreBigInt53Builder(name, config.unsigned);
	if (config.mode === "string") return new SingleStoreBigIntStringBuilder(name, config.unsigned);
	return new SingleStoreBigInt64Builder(name, config.unsigned);
}

//#endregion
export { SingleStoreBigInt53, SingleStoreBigInt53Builder, SingleStoreBigInt64, SingleStoreBigInt64Builder, SingleStoreBigIntString, SingleStoreBigIntStringBuilder, bigint };
//# sourceMappingURL=bigint.js.map