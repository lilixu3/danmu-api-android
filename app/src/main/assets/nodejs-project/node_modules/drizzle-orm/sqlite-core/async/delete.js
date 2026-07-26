import { extractUsedTable } from "../utils.js";
import { entityKind } from "../../entity.js";
import { applyMixins } from "../../utils.js";
import { QueryPromise } from "../../query-promise.js";
import { SQLiteDeleteBase } from "../query-builders/delete.js";

//#region src/sqlite-core/async/delete.ts
var SQLiteAsyncDeleteBase = class extends SQLiteDeleteBase {
	static [entityKind] = "SQLiteAsyncDelete";
	/** @internal */
	_prepare(prepare = false) {
		return this.session.prepareQuery(this.dialect.sqlToQuery(this.getSQL()), "arrays", prepare, this.config.returning ? "all" : "run", this.config.returning ? this.dialect.mapperGenerators.rows(this.config.returning, void 0) : void 0, {
			type: "delete",
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
	async execute(placeholderValues) {
		return this._prepare().execute(placeholderValues);
	}
};
applyMixins(SQLiteAsyncDeleteBase, [QueryPromise]);

//#endregion
export { SQLiteAsyncDeleteBase };
//# sourceMappingURL=delete.js.map