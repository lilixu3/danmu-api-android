import { SingleStoreColumnBuilderWithAutoIncrement, SingleStoreColumnWithAutoIncrement } from "./common.js";
import { entityKind } from "../../entity.js";
import { getColumnNameAndConfig } from "../../utils.js";

//#region src/singlestore-core/columns/mediumint.ts
var SingleStoreMediumIntBuilder = class extends SingleStoreColumnBuilderWithAutoIncrement {
	static [entityKind] = "SingleStoreMediumIntBuilder";
	constructor(name, config) {
		super(name, config?.unsigned ? "number uint24" : "number int24", "SingleStoreMediumInt");
		this.config.unsigned = config ? config.unsigned : false;
	}
	/** @internal */
	build(table) {
		return new SingleStoreMediumInt(table, this.config);
	}
};
var SingleStoreMediumInt = class extends SingleStoreColumnWithAutoIncrement {
	static [entityKind] = "SingleStoreMediumInt";
	getSQLType() {
		return `mediumint${this.config.unsigned ? " unsigned" : ""}`;
	}
	mapFromDriverValue = (value) => {
		if (typeof value === "string") return Number(value);
		return value;
	};
};
function mediumint(a, b) {
	const { name, config } = getColumnNameAndConfig(a, b);
	return new SingleStoreMediumIntBuilder(name, config);
}

//#endregion
export { SingleStoreMediumInt, SingleStoreMediumIntBuilder, mediumint };
//# sourceMappingURL=mediumint.js.map