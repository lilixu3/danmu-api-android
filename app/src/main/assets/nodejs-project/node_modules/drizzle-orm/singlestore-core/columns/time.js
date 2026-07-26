import { SingleStoreColumn, SingleStoreColumnBuilder } from "./common.js";
import { entityKind } from "../../entity.js";

//#region src/singlestore-core/columns/time.ts
var SingleStoreTimeBuilder = class extends SingleStoreColumnBuilder {
	static [entityKind] = "SingleStoreTimeBuilder";
	constructor(name) {
		super(name, "string time", "SingleStoreTime");
	}
	/** @internal */
	build(table) {
		return new SingleStoreTime(table, this.config);
	}
};
var SingleStoreTime = class extends SingleStoreColumn {
	static [entityKind] = "SingleStoreTime";
	getSQLType() {
		return `time`;
	}
};
function time(name) {
	return new SingleStoreTimeBuilder(name ?? "");
}

//#endregion
export { SingleStoreTime, SingleStoreTimeBuilder, time };
//# sourceMappingURL=time.js.map