Object.defineProperty(exports, Symbol.toStringTag, { value: 'Module' });
const require_runtime = require('../../_virtual/_rolldown/runtime.cjs');
let __entity_ts = require("../../entity.cjs");
let __utils_ts = require("../../utils.cjs");
let __query_promise_ts = require("../../query-promise.cjs");
let __sqlite_core_query_builders_count_ts = require("../query-builders/count.cjs");

//#region src/sqlite-core/async/count.ts
var SQLiteAsyncCountBuilder = class extends __sqlite_core_query_builders_count_ts.SQLiteCountBuilder {
	static [__entity_ts.entityKind] = "SQLiteAsyncCountBuilder";
	constructor(countConfig) {
		super(countConfig);
	}
	/** @internal */
	executeRaw(placeholderValues) {
		return this.session.prepareQuery(this.build(), "arrays", false, "all", (rows) => {
			const v = rows[0]?.[0];
			if (typeof v === "number") return v;
			return v ? Number(v) : 0;
		}).execute(placeholderValues);
	}
	async execute(placeholderValues) {
		return await this.executeRaw(placeholderValues);
	}
};
(0, __utils_ts.applyMixins)(SQLiteAsyncCountBuilder, [__query_promise_ts.QueryPromise]);
var SQLiteSyncCountBuilder = class extends SQLiteAsyncCountBuilder {
	static [__entity_ts.entityKind] = "SQLiteSyncCountBuilder";
	sync(placeholderValues) {
		return this.executeRaw(placeholderValues).sync();
	}
};

//#endregion
exports.SQLiteAsyncCountBuilder = SQLiteAsyncCountBuilder;
exports.SQLiteSyncCountBuilder = SQLiteSyncCountBuilder;
//# sourceMappingURL=count.cjs.map