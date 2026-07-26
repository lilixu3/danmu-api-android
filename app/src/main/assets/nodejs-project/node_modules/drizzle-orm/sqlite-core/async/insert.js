import { extractUsedTable } from "../utils.js";
import { entityKind } from "../../entity.js";
import { applyMixins } from "../../utils.js";
import { QueryPromise } from "../../query-promise.js";
import { SQLiteInsertBase } from "../query-builders/insert.js";

//#region src/sqlite-core/async/insert.ts
var SQLiteAsyncInsertBase = class extends SQLiteInsertBase {
	static [entityKind] = "SQLiteAsyncInsert";
	/** @internal */
	_prepare(prepare = false) {
		return this.session.prepareQuery(this.dialect.sqlToQuery(this.getSQL()), "arrays", prepare, this.config.returning ? "all" : "run", this.config.returning ? this.dialect.mapperGenerators.rows(this.config.returning, void 0) : void 0, {
			type: "insert",
			tables: extractUsedTable(this.config.table)
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
applyMixins(SQLiteAsyncInsertBase, [QueryPromise]);

//#endregion
export { SQLiteAsyncInsertBase };
//# sourceMappingURL=insert.js.map