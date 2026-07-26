import { SQLiteColumn, SQLiteColumnBuilder } from "./common.js";
import { entityKind } from "../../entity.js";

//#region src/sqlite-core/columns/real.ts
var SQLiteRealBuilder = class extends SQLiteColumnBuilder {
	static [entityKind] = "SQLiteRealBuilder";
	constructor(name) {
		super(name, "number double", "SQLiteReal");
	}
	/** @internal */
	build(table) {
		return new SQLiteReal(table, this.config);
	}
};
var SQLiteReal = class extends SQLiteColumn {
	static [entityKind] = "SQLiteReal";
	getSQLType() {
		return "real";
	}
};
function real(name) {
	return new SQLiteRealBuilder(name ?? "");
}

//#endregion
export { SQLiteReal, SQLiteRealBuilder, real };
//# sourceMappingURL=real.js.map