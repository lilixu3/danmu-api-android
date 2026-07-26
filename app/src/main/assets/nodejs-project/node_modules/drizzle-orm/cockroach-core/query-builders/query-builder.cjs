Object.defineProperty(exports, Symbol.toStringTag, { value: 'Module' });
const require_runtime = require('../../_virtual/_rolldown/runtime.cjs');
const require_cockroach_core_query_builders_select = require('./select.cjs');
let __entity_ts = require("../../entity.cjs");
let __subquery_ts = require("../../subquery.cjs");
let __selection_proxy_ts = require("../../selection-proxy.cjs");
let __cockroach_core_dialect_ts = require("../dialect.cjs");

//#region src/cockroach-core/query-builders/query-builder.ts
var QueryBuilder = class {
	static [__entity_ts.entityKind] = "CockroachQueryBuilder";
	dialect;
	dialectConfig;
	constructor(dialect) {
		this.dialect = (0, __entity_ts.is)(dialect, __cockroach_core_dialect_ts.CockroachDialect) ? dialect : void 0;
		this.dialectConfig = (0, __entity_ts.is)(dialect, __cockroach_core_dialect_ts.CockroachDialect) ? void 0 : dialect;
	}
	$with = (alias, selection) => {
		const queryBuilder = this;
		const as = (qb) => {
			if (typeof qb === "function") qb = qb(queryBuilder);
			return new Proxy(new __subquery_ts.WithSubquery(qb.getSQL(), selection ?? ("getSelectedFields" in qb ? qb.getSelectedFields() ?? {} : {}), alias, true), new __selection_proxy_ts.SelectionProxyHandler({
				alias,
				sqlAliasedBehavior: "alias",
				sqlBehavior: "error"
			}));
		};
		return { as };
	};
	with(...queries) {
		const self = this;
		function select(fields) {
			return new require_cockroach_core_query_builders_select.CockroachSelectBuilder({
				fields: fields ?? void 0,
				session: void 0,
				dialect: self.getDialect(),
				withList: queries
			});
		}
		function selectDistinct(fields) {
			return new require_cockroach_core_query_builders_select.CockroachSelectBuilder({
				fields: fields ?? void 0,
				session: void 0,
				dialect: self.getDialect(),
				distinct: true
			});
		}
		function selectDistinctOn(on, fields) {
			return new require_cockroach_core_query_builders_select.CockroachSelectBuilder({
				fields: fields ?? void 0,
				session: void 0,
				dialect: self.getDialect(),
				distinct: { on }
			});
		}
		return {
			select,
			selectDistinct,
			selectDistinctOn
		};
	}
	select(fields) {
		return new require_cockroach_core_query_builders_select.CockroachSelectBuilder({
			fields: fields ?? void 0,
			session: void 0,
			dialect: this.getDialect()
		});
	}
	selectDistinct(fields) {
		return new require_cockroach_core_query_builders_select.CockroachSelectBuilder({
			fields: fields ?? void 0,
			session: void 0,
			dialect: this.getDialect(),
			distinct: true
		});
	}
	selectDistinctOn(on, fields) {
		return new require_cockroach_core_query_builders_select.CockroachSelectBuilder({
			fields: fields ?? void 0,
			session: void 0,
			dialect: this.getDialect(),
			distinct: { on }
		});
	}
	getDialect() {
		if (!this.dialect) this.dialect = new __cockroach_core_dialect_ts.CockroachDialect(this.dialectConfig);
		return this.dialect;
	}
};

//#endregion
exports.QueryBuilder = QueryBuilder;
//# sourceMappingURL=query-builder.cjs.map