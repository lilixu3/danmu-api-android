Object.defineProperty(exports, Symbol.toStringTag, { value: 'Module' });
const require_runtime = require('../../_virtual/_rolldown/runtime.cjs');
let __entity_ts = require("../../entity.cjs");
let __utils_ts = require("../../utils.cjs");
let __query_promise_ts = require("../../query-promise.cjs");
let __sqlite_core_query_builders_raw_ts = require("../query-builders/raw.cjs");

//#region src/sqlite-core/async/raw.ts
var SQLiteAsyncRaw = class extends __sqlite_core_query_builders_raw_ts.SQLiteRaw {
	static [__entity_ts.entityKind] = "SQLiteAsyncRaw";
	constructor(prepared, sql, query) {
		super(prepared, sql, query);
	}
	execute(placeholderValues) {
		return this.prepared.execute(placeholderValues);
	}
};
(0, __utils_ts.applyMixins)(SQLiteAsyncRaw, [__query_promise_ts.QueryPromise]);

//#endregion
exports.SQLiteAsyncRaw = SQLiteAsyncRaw;
//# sourceMappingURL=raw.cjs.map