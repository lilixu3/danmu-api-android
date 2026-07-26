import { SingleStoreColumnBuilderWithAutoIncrement, SingleStoreColumnWithAutoIncrement } from "./common.js";
import { entityKind } from "../../entity.js";
import { getColumnNameAndConfig } from "../../utils.js";

//#region src/singlestore-core/columns/smallint.ts
var SingleStoreSmallIntBuilder = class extends SingleStoreColumnBuilderWithAutoIncrement {
	static [entityKind] = "SingleStoreSmallIntBuilder";
	constructor(name, config) {
		super(name, config?.unsigned ? "number uint16" : "number int16", "SingleStoreSmallInt");
		this.config.unsigned = config ? config.unsigned : false;
	}
	/** @internal */
	build(table) {
		return new SingleStoreSmallInt(table, this.config);
	}
};
var SingleStoreSmallInt = class extends SingleStoreColumnWithAutoIncrement {
	static [entityKind] = "SingleStoreSmallInt";
	getSQLType() {
		return `smallint${this.config.unsigned ? " unsigned" : ""}`;
	}
	mapFromDriverValue = (value) => {
		if (typeof value === "string") return Number(value);
		return value;
	};
};
function smallint(a, b) {
	const { name, config } = getColumnNameAndConfig(a, b);
	return new SingleStoreSmallIntBuilder(name, config);
}

//#endregion
export { SingleStoreSmallInt, SingleStoreSmallIntBuilder, smallint };
//# sourceMappingURL=smallint.js.map