import { entityKind } from "../../entity.js";

//#region src/pg-core/query-builders/refresh-materialized-view.ts
var PgRefreshMaterializedView = class {
	static [entityKind] = "PgRefreshMaterializedView";
	config;
	constructor(view, session, dialect) {
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
};

//#endregion
export { PgRefreshMaterializedView };
//# sourceMappingURL=refresh-materialized-view.js.map