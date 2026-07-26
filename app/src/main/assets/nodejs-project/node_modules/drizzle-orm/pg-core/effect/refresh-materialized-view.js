import { PgRefreshMaterializedView } from "../query-builders/refresh-materialized-view.js";
import { entityKind } from "../../entity.js";
import { applyEffectWrapper } from "../../effect-core/query-effect.js";

//#region src/pg-core/effect/refresh-materialized-view.ts
var PgEffectRefreshMaterializedView = class extends PgRefreshMaterializedView {
	static [entityKind] = "PgEffectRefreshMaterializedView";
	/** @internal */
	_prepare(name, generateName = false) {
		const query = this.dialect.sqlToQuery(this.getSQL());
		return this.session.prepareQuery(query, "raw", name ?? generateName);
	}
	prepare(name) {
		return this._prepare(name, true);
	}
	execute = (placeholderValues) => {
		return this._prepare().execute(placeholderValues);
	};
};
applyEffectWrapper(PgEffectRefreshMaterializedView);

//#endregion
export { PgEffectRefreshMaterializedView };
//# sourceMappingURL=refresh-materialized-view.js.map