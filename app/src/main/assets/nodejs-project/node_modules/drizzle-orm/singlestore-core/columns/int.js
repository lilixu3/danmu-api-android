import { SingleStoreColumnBuilderWithAutoIncrement, SingleStoreColumnWithAutoIncrement } from "./common.js";
import { entityKind } from "../../entity.js";
import { getColumnNameAndConfig } from "../../utils.js";

//#region src/singlestore-core/columns/int.ts
var SingleStoreIntBuilder = class extends SingleStoreColumnBuilderWithAutoIncrement {
	static [entityKind] = "SingleStoreIntBuilder";
	constructor(name, config) {
		super(name, config?.unsigned ? "number uint32" : "number int32", "SingleStoreInt");
		this.config.unsigned = config ? config.unsigned : false;
	}
	/** @internal */
	build(table) {
		return new SingleStoreInt(table, this.config);
	}
};
var SingleStoreInt = class extends SingleStoreColumnWithAutoIncrement {
	static [entityKind] = "SingleStoreInt";
	getSQLType() {
		return `int${this.config.unsigned ? " unsigned" : ""}`;
	}
	mapFromDriverValue = (value) => {
		if (typeof value === "string") return Number(value);
		return value;
	};
};
function int(a, b) {
	const { name, config } = getColumnNameAndConfig(a, b);
	return new SingleStoreIntBuilder(name, config);
}

//#endregion
export { SingleStoreInt, SingleStoreIntBuilder, int };
//# sourceMappingURL=int.js.map