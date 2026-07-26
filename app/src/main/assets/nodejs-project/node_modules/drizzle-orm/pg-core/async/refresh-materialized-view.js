import { PgRefreshMaterializedView } from "../query-builders/refresh-materialized-view.js";
import { entityKind } from "../../entity.js";
import { tracer } from "../../tracing.js";
import { applyMixins } from "../../utils.js";
import { QueryPromise } from "../../query-promise.js";

//#region src/pg-core/async/refresh-materialized-view.ts
var PgAsyncRefreshMaterializedView = class extends PgRefreshMaterializedView {
	static [entityKind] = "PgAsyncRefreshMaterializedView";
	/** @internal */
	_prepare(name, generateName = false) {
		return tracer.startActiveSpan("drizzle.prepareQuery", () => {
			const query = this.dialect.sqlToQuery(this.getSQL());
			return this.session.prepareQuery(query, "raw", name ?? generateName);
		});
	}
	prepare(name) {
		return this._prepare(name, true);
	}
	execute = (placeholderValues) => {
		return tracer.startActiveSpan("drizzle.operation", () => {
			return this._prepare().execute(placeholderValues);
		});
	};
};
applyMixins(PgAsyncRefreshMaterializedView, [QueryPromise]);

//#endregion
export { PgAsyncRefreshMaterializedView };
//# sourceMappingURL=refresh-materialized-view.js.map