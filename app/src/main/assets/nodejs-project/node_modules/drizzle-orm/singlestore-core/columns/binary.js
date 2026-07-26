import { SingleStoreColumn, SingleStoreColumnBuilder } from "./common.js";
import { entityKind } from "../../entity.js";
import { getColumnNameAndConfig } from "../../utils.js";

//#region src/singlestore-core/columns/binary.ts
var SingleStoreBinaryBuilder = class extends SingleStoreColumnBuilder {
	static [entityKind] = "SingleStoreBinaryBuilder";
	constructor(name, length) {
		super(name, "string binary", "SingleStoreBinary");
		this.config.length = length ?? 1;
		this.config.setLength = length !== void 0;
	}
	/** @internal */
	build(table) {
		return new SingleStoreBinary(table, this.config);
	}
};
var SingleStoreBinary = class extends SingleStoreColumn {
	static [entityKind] = "SingleStoreBinary";
	mapFromDriverValue = (value) => {
		if (typeof value === "string") return value;
		if (Buffer.isBuffer(value)) return value.toString();
		const str = [];
		for (const v of value) str.push(v === 49 ? "1" : "0");
		return str.join("");
	};
	getSQLType() {
		return this.config.setLength ? `binary(${this.length})` : `binary`;
	}
};
function binary(a, b = {}) {
	const { name, config } = getColumnNameAndConfig(a, b);
	return new SingleStoreBinaryBuilder(name, config.length);
}

//#endregion
export { SingleStoreBinary, SingleStoreBinaryBuilder, binary };
//# sourceMappingURL=binary.js.map