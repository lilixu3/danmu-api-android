import { SingleStoreColumn, SingleStoreColumnBuilder } from "./common.js";
import { entityKind } from "../../entity.js";

//#region src/singlestore-core/columns/boolean.ts
var SingleStoreBooleanBuilder = class extends SingleStoreColumnBuilder {
	static [entityKind] = "SingleStoreBooleanBuilder";
	constructor(name) {
		super(name, "boolean", "SingleStoreBoolean");
	}
	/** @internal */
	build(table) {
		return new SingleStoreBoolean(table, this.config);
	}
};
var SingleStoreBoolean = class extends SingleStoreColumn {
	static [entityKind] = "SingleStoreBoolean";
	getSQLType() {
		return "boolean";
	}
	mapFromDriverValue = (value) => {
		if (typeof value === "boolean") return value;
		return value === 1;
	};
};
function boolean(name) {
	return new SingleStoreBooleanBuilder(name ?? "");
}

//#endregion
export { SingleStoreBoolean, SingleStoreBooleanBuilder, boolean };
//# sourceMappingURL=boolean.js.map