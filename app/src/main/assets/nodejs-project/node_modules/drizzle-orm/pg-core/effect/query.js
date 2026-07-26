import { PgRelationalQuery } from "../query-builders/query.js";
import { entityKind } from "../../entity.js";
import { applyEffectWrapper } from "../../effect-core/query-effect.js";

//#region src/pg-core/effect/query.ts
var PgEffectRelationalQuery = class extends PgRelationalQuery {
	static [entityKind] = "PgEffectRelationalQueryV2";
	/** @internal */
	_prepare(name, generateName = false) {
		const { query, builtQuery } = this._toSQL();
		const mapper = this.dialect.mapperGenerators.relationalRows({
			isFirst: this.mode === "first",
			parseJson: this.parseJson,
			parseJsonIfString: false,
			rootJsonMappers: false,
			selection: query.selection,
			arrayModeRoot: true
		});
		return this.session.prepareQuery(builtQuery, "arrays", name ?? generateName, mapper);
	}
	prepare(name) {
		return this._prepare(name, true);
	}
	execute(placeholderValues) {
		return this._prepare().execute(placeholderValues);
	}
};
applyEffectWrapper(PgEffectRelationalQuery);

//#endregion
export { PgEffectRelationalQuery };
//# sourceMappingURL=query.js.map