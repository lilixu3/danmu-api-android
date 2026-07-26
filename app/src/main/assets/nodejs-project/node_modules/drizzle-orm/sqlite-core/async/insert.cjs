Object.defineProperty(exports, Symbol.toStringTag, { value: 'Module' });
const require_runtime = require('../../_virtual/_rolldown/runtime.cjs');
const require_sqlite_core_utils = require('../utils.cjs');
let __entity_ts = require("../../entity.cjs");
let __utils_ts = require("../../utils.cjs");
let __query_promise_ts = require("../../query-promise.cjs");
let __sqlite_core_query_builders_insert_ts = require("../query-builders/insert.cjs");

//#region src/sqlite-core/async/insert.ts
var SQLiteAsyncInsertBase = class extends __sqlite_core_query_builders_insert_ts.SQLiteInsertBase {
	static [__entity_ts.entityKind] = "SQLiteAsyncInsert";
	/** @internal */
	_prepare(prepare = false) {
		return this.session.prepareQuery(this.dialect.sqlToQuery(this.getSQL()), "arrays", prepare, this.config.returning ? "all" : "run", this.config.returning ? this.dialect.mapperGenerators.rows(this.config.returning, void 0) : void 0, {
			type: "insert",
			tables: require_sqlite_core_utils.extractUsedTable(this.config.table)
		});
	}
	prepare() {
		return this._prepare(true);
	}
	run = (placeholderValues) => {
		return this._prepare().run(placeholderValues);
	};
	all = (placeholderValues) => {
		return this._prepare().all(placeholderValues);
	};
	get = (placeholderValues) => {
		return this._prepare().get(placeholderValues);
	};
	values = (placeholderValues) => {
		return this._prepare().values(placeholderValues);
	};
	async execute() {
		return this._prepare().execute();
	}
};
(0, __utils_ts.applyMixins)(SQLiteAsyncInsertBase, [__query_promise_ts.QueryPromise]);

//#endregion
exports.SQLiteAsyncInsertBase = SQLiteAsyncInsertBase;
//# sourceMappingURL=insert.cjs.map