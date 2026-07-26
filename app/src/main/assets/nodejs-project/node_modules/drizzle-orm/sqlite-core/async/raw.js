import { entityKind } from "../../entity.js";
import { applyMixins } from "../../utils.js";
import { QueryPromise } from "../../query-promise.js";
import { SQLiteRaw } from "../query-builders/raw.js";

//#region src/sqlite-core/async/raw.ts
var SQLiteAsyncRaw = class extends SQLiteRaw {
	static [entityKind] = "SQLiteAsyncRaw";
	constructor(prepared, sql, query) {
		super(prepared, sql, query);
	}
	execute(placeholderValues) {
		return this.prepared.execute(placeholderValues);
	}
};
applyMixins(SQLiteAsyncRaw, [QueryPromise]);

//#endregion
export { SQLiteAsyncRaw };
//# sourceMappingURL=raw.js.map