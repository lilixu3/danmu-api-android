Object.defineProperty(exports, Symbol.toStringTag, { value: 'Module' });
const require_runtime = require('../../_virtual/_rolldown/runtime.cjs');
let __entity_ts = require("../../entity.cjs");
let __query_promise_ts = require("../../query-promise.cjs");

//#region src/cockroach-core/query-builders/raw.ts
var CockroachRaw = class extends __query_promise_ts.QueryPromise {
	static [__entity_ts.entityKind] = "CockroachRaw";
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
exports.CockroachRaw = CockroachRaw;
//# sourceMappingURL=raw.cjs.map