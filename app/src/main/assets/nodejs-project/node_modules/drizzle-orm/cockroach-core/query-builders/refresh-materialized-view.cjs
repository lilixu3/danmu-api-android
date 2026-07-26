Object.defineProperty(exports, Symbol.toStringTag, { value: 'Module' });
const require_runtime = require('../../_virtual/_rolldown/runtime.cjs');
let __entity_ts = require("../../entity.cjs");
let __tracing_ts = require("../../tracing.cjs");
let __query_name_generator_ts = require("../../query-name-generator.cjs");
let __query_promise_ts = require("../../query-promise.cjs");

//#region src/cockroach-core/query-builders/refresh-materialized-view.ts
var CockroachRefreshMaterializedView = class extends __query_promise_ts.QueryPromise {
	static [__entity_ts.entityKind] = "CockroachRefreshMaterializedView";
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
		return __tracing_ts.tracer.startActiveSpan("drizzle.prepareQuery", () => {
			const query = this.dialect.sqlToQuery(this.getSQL());
			return this.session.prepareQuery(query, void 0, name ?? (generateName ? (0, __query_name_generator_ts.preparedStatementName)(query.sql, query.params) : name));
		});
	}
	prepare(name) {
		return this._prepare(name, true);
	}
	execute = (placeholderValues) => {
		return __tracing_ts.tracer.startActiveSpan("drizzle.operation", () => {
			return this._prepare().execute(placeholderValues);
		});
	};
};

//#endregion
exports.CockroachRefreshMaterializedView = CockroachRefreshMaterializedView;
//# sourceMappingURL=refresh-materialized-view.cjs.map