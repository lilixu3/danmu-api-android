Object.defineProperty(exports, Symbol.toStringTag, { value: 'Module' });
const require_runtime = require('../../_virtual/_rolldown/runtime.cjs');
let __entity_ts = require("../../entity.cjs");
let __utils_ts = require("../../utils.cjs");
let __query_promise_ts = require("../../query-promise.cjs");
let __sqlite_core_query_builders_query_ts = require("../query-builders/query.cjs");

//#region src/sqlite-core/async/query.ts
var SQLiteAsyncRelationalQuery = class extends __sqlite_core_query_builders_query_ts.SQLiteRelationalQuery {
	static [__entity_ts.entityKind] = "SQLiteAsyncRelationalQueryV2";
	/** @internal */
	_prepare(prepare = false) {
		const { query, builtQuery } = this._toSQL();
		const mapper = this.dialect.mapperGenerators.relationalRows({
			isFirst: this.mode === "first",
			parseJson: true,
			parseJsonIfString: false,
			rootJsonMappers: false,
			selection: query.selection,
			arrayModeRoot: true
		});
		return this.session.prepareQuery(builtQuery, "arrays", prepare, "all", mapper);
	}
	prepare() {
		return this._prepare(true);
	}
	async execute(placeholderValues) {
		return this._prepare().execute(placeholderValues);
	}
};
var SQLiteSyncRelationalQuery = class extends SQLiteAsyncRelationalQuery {
	static [__entity_ts.entityKind] = "SQLiteSyncRelationalQueryV2";
	sync(placeholderValues) {
		return this._prepare().execute(placeholderValues).sync();
	}
};
(0, __utils_ts.applyMixins)(SQLiteAsyncRelationalQuery, [__query_promise_ts.QueryPromise]);

//#endregion
exports.SQLiteAsyncRelationalQuery = SQLiteAsyncRelationalQuery;
exports.SQLiteSyncRelationalQuery = SQLiteSyncRelationalQuery;
//# sourceMappingURL=query.cjs.map