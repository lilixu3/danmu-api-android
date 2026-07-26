import { extractUsedTable } from "../utils.js";
import { entityKind } from "../../entity.js";
import { applyEffectWrapper } from "../../effect-core/query-effect.js";
import { SQLiteDeleteBase } from "../query-builders/delete.js";

//#region src/sqlite-core/effect/delete.ts
var SQLiteEffectDeleteBase = class extends SQLiteDeleteBase {
	static [entityKind] = "SQLiteEffectDelete";
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
	execute = (placeholderValues) => {
		return this._prepare().execute(placeholderValues);
	};
};
applyEffectWrapper(SQLiteEffectDeleteBase);

//#endregion
export { SQLiteEffectDeleteBase };
//# sourceMappingURL=delete.js.map