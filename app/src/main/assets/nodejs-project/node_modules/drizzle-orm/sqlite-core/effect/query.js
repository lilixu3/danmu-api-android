import { entityKind } from "../../entity.js";
import { applyEffectWrapper } from "../../effect-core/query-effect.js";
import { SQLiteRelationalQuery } from "../query-builders/query.js";

//#region src/sqlite-core/effect/query.ts
var SQLiteEffectRelationalQuery = class extends SQLiteRelationalQuery {
	static [entityKind] = "SQLiteEffectRelationalQueryV2";
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
	execute(placeholderValues) {
		return this._prepare().execute(placeholderValues);
	}
};
applyEffectWrapper(SQLiteEffectRelationalQuery);

//#endregion
export { SQLiteEffectRelationalQuery };
//# sourceMappingURL=query.js.map