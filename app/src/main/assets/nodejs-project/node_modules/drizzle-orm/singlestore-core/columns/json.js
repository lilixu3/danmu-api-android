import { SingleStoreColumn, SingleStoreColumnBuilder } from "./common.js";
import { entityKind } from "../../entity.js";

//#region src/singlestore-core/columns/json.ts
var SingleStoreJsonBuilder = class extends SingleStoreColumnBuilder {
	static [entityKind] = "SingleStoreJsonBuilder";
	constructor(name) {
		super(name, "object json", "SingleStoreJson");
	}
	/** @internal */
	build(table) {
		return new SingleStoreJson(table, this.config);
	}
};
var SingleStoreJson = class extends SingleStoreColumn {
	static [entityKind] = "SingleStoreJson";
	getSQLType() {
		return "json";
	}
	mapToDriverValue = (value) => {
		return JSON.stringify(value);
	};
};
function json(name) {
	return new SingleStoreJsonBuilder(name ?? "");
}

//#endregion
export { SingleStoreJson, SingleStoreJsonBuilder, json };
//# sourceMappingURL=json.js.map