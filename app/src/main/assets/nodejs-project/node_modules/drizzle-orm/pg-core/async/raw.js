import { PgRaw } from "../query-builders/raw.js";
import { entityKind } from "../../entity.js";
import { applyMixins } from "../../utils.js";
import { QueryPromise } from "../../query-promise.js";

//#region src/pg-core/async/raw.ts
var PgAsyncRaw = class extends PgRaw {
	static [entityKind] = "PgAsyncRaw";
	constructor(prepared, sql, query) {
		super(prepared, sql, query);
	}
	execute(placeholderValues) {
		return this.prepared.execute(placeholderValues);
	}
	_prepare() {
		return this.prepared;
	}
};
applyMixins(PgAsyncRaw, [QueryPromise]);

//#endregion
export { PgAsyncRaw };
//# sourceMappingURL=raw.js.map