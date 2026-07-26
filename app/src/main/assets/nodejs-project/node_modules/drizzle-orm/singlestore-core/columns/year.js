import { SingleStoreColumn, SingleStoreColumnBuilder } from "./common.js";
import { entityKind } from "../../entity.js";

//#region src/singlestore-core/columns/year.ts
var SingleStoreYearBuilder = class extends SingleStoreColumnBuilder {
	static [entityKind] = "SingleStoreYearBuilder";
	constructor(name) {
		super(name, "number year", "SingleStoreYear");
	}
	/** @internal */
	build(table) {
		return new SingleStoreYear(table, this.config);
	}
};
var SingleStoreYear = class extends SingleStoreColumn {
	static [entityKind] = "SingleStoreYear";
	getSQLType() {
		return `year`;
	}
	mapFromDriverValue = (value) => {
		if (typeof value !== "number") return Number(value);
		return value;
	};
};
function year(name) {
	return new SingleStoreYearBuilder(name ?? "");
}

//#endregion
export { SingleStoreYear, SingleStoreYearBuilder, year };
//# sourceMappingURL=year.js.map