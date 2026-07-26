import { SingleStoreColumnBuilderWithAutoIncrement, SingleStoreColumnWithAutoIncrement } from "./common.js";
import { entityKind } from "../../entity.js";
import { getColumnNameAndConfig } from "../../utils.js";

//#region src/singlestore-core/columns/tinyint.ts
var SingleStoreTinyIntBuilder = class extends SingleStoreColumnBuilderWithAutoIncrement {
	static [entityKind] = "SingleStoreTinyIntBuilder";
	constructor(name, config) {
		super(name, config?.unsigned ? "number uint8" : "number int8", "SingleStoreTinyInt");
		this.config.unsigned = config ? config.unsigned : false;
	}
	/** @internal */
	build(table) {
		return new SingleStoreTinyInt(table, this.config);
	}
};
var SingleStoreTinyInt = class extends SingleStoreColumnWithAutoIncrement {
	static [entityKind] = "SingleStoreTinyInt";
	getSQLType() {
		return `tinyint${this.config.unsigned ? " unsigned" : ""}`;
	}
	mapFromDriverValue = (value) => {
		if (typeof value === "string") return Number(value);
		return value;
	};
};
function tinyint(a, b) {
	const { name, config } = getColumnNameAndConfig(a, b);
	return new SingleStoreTinyIntBuilder(name, config);
}

//#endregion
export { SingleStoreTinyInt, SingleStoreTinyIntBuilder, tinyint };
//# sourceMappingURL=tinyint.js.map