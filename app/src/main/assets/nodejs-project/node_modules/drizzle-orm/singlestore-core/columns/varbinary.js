import { SingleStoreColumn, SingleStoreColumnBuilder } from "./common.js";
import { entityKind } from "../../entity.js";
import { getColumnNameAndConfig } from "../../utils.js";

//#region src/singlestore-core/columns/varbinary.ts
var SingleStoreVarBinaryBuilder = class extends SingleStoreColumnBuilder {
	static [entityKind] = "SingleStoreVarBinaryBuilder";
	/** @internal */
	constructor(name, config) {
		super(name, "string binary", "SingleStoreVarBinary");
		this.config.length = config.length;
	}
	/** @internal */
	build(table) {
		return new SingleStoreVarBinary(table, this.config);
	}
};
var SingleStoreVarBinary = class extends SingleStoreColumn {
	static [entityKind] = "SingleStoreVarBinary";
	mapFromDriverValue = (value) => {
		if (typeof value === "string") return value;
		if (Buffer.isBuffer(value)) return value.toString();
		const str = [];
		for (const v of value) str.push(v === 49 ? "1" : "0");
		return str.join("");
	};
	getSQLType() {
		return this.length === void 0 ? `varbinary` : `varbinary(${this.length})`;
	}
};
function varbinary(a, b) {
	const { name, config } = getColumnNameAndConfig(a, b);
	return new SingleStoreVarBinaryBuilder(name, config);
}

//#endregion
export { SingleStoreVarBinary, SingleStoreVarBinaryBuilder, varbinary };
//# sourceMappingURL=varbinary.js.map