import { extractUsedTable } from "../utils.js";
import { entityKind } from "../../entity.js";
import { applyEffectWrapper } from "../../effect-core/query-effect.js";
import { SQLiteUpdateBase } from "../query-builders/update.js";

//#region src/sqlite-core/effect/update.ts
var SQLiteEffectUpdateBase = class extends SQLiteUpdateBase {
	static [entityKind] = "SQLiteEffectUpdate";
	/** @internal */
	_prepare(prepare = false) {
		return this.session.prepareQuery(this.dialect.sqlToQuery(this.getSQL()), "arrays", prepare, this.config.returning ? "all" : "run", this.config.returning ? this.dialect.mapperGenerators.rows(this.config.returning, void 0) : void 0, {
			type: "update",
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
applyEffectWrapper(SQLiteEffectUpdateBase);

//#endregion
export { SQLiteEffectUpdateBase };
//# sourceMappingURL=update.js.map