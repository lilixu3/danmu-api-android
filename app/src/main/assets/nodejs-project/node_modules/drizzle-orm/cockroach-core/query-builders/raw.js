import { entityKind } from "../../entity.js";
import { QueryPromise } from "../../query-promise.js";

//#region src/cockroach-core/query-builders/raw.ts
var CockroachRaw = class extends QueryPromise {
	static [entityKind] = "CockroachRaw";
	constructor(execute, sql, query, mapBatchResult) {
		super();
		this.execute = execute;
		this.sql = sql;
		this.query = query;
		this.mapBatchResult = mapBatchResult;
	}
	/** @internal */
	getSQL() {
		return this.sql;
	}
	getQuery() {
		return this.query;
	}
	mapResult(result, isFromBatch) {
		return isFromBatch ? this.mapBatchResult(result) : result;
	}
	_prepare() {
		return this;
	}
};

//#endregion
export { CockroachRaw };
//# sourceMappingURL=raw.js.map