import { entityKind } from "../../entity.js";
import { tracer } from "../../tracing.js";
import { preparedStatementName } from "../../query-name-generator.js";
import { QueryPromise } from "../../query-promise.js";

//#region src/cockroach-core/query-builders/refresh-materialized-view.ts
var CockroachRefreshMaterializedView = class extends QueryPromise {
	static [entityKind] = "CockroachRefreshMaterializedView";
	config;
	constructor(view, session, dialect) {
		super();
		this.session = session;
		this.dialect = dialect;
		this.config = { view };
	}
	concurrently() {
		if (this.config.withNoData !== void 0) throw new Error("Cannot use concurrently and withNoData together");
		this.config.concurrently = true;
		return this;
	}
	withNoData() {
		if (this.config.concurrently !== void 0) throw new Error("Cannot use concurrently and withNoData together");
		this.config.withNoData = true;
		return this;
	}
	/** @internal */
	getSQL() {
		return this.dialect.buildRefreshMaterializedViewQuery(this.config);
	}
	toSQL() {
		return this.dialect.sqlToQuery(this.getSQL());
	}
	/** @internal */
	_prepare(name, generateName = false) {
		return tracer.startActiveSpan("drizzle.prepareQuery", () => {
			const query = this.dialect.sqlToQuery(this.getSQL());
			return this.session.prepareQuery(query, void 0, name ?? (generateName ? preparedStatementName(query.sql, query.params) : name));
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

//#endregion
export { CockroachRefreshMaterializedView };
//# sourceMappingURL=refresh-materialized-view.js.map