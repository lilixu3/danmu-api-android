import { entityKind } from "../../entity.js";
import { applyMixins } from "../../utils.js";
import { QueryPromise } from "../../query-promise.js";
import { SQLiteRelationalQuery } from "../query-builders/query.js";

//#region src/sqlite-core/async/query.ts
var SQLiteAsyncRelationalQuery = class extends SQLiteRelationalQuery {
	static [entityKind] = "SQLiteAsyncRelationalQueryV2";
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
	static [entityKind] = "SQLiteSyncRelationalQueryV2";
	sync(placeholderValues) {
		return this._prepare().execute(placeholderValues).sync();
	}
};
applyMixins(SQLiteAsyncRelationalQuery, [QueryPromise]);

//#endregion
export { SQLiteAsyncRelationalQuery, SQLiteSyncRelationalQuery };
//# sourceMappingURL=query.js.map