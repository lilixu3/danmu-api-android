Object.defineProperty(exports, Symbol.toStringTag, { value: 'Module' });
const require_runtime = require('../../_virtual/_rolldown/runtime.cjs');
const require_mssql_core_query_builders_select = require('./select.cjs');
let __entity_ts = require("../../entity.cjs");
let __subquery_ts = require("../../subquery.cjs");
let __selection_proxy_ts = require("../../selection-proxy.cjs");
let __mssql_core_dialect_ts = require("../dialect.cjs");

//#region src/mssql-core/query-builders/query-builder.ts
var QueryBuilder = class {
	static [__entity_ts.entityKind] = "MsSqlQueryBuilder";
	dialect;
	dialectConfig;
	constructor(dialect) {
		this.dialect = (0, __entity_ts.is)(dialect, __mssql_core_dialect_ts.MsSqlDialect) ? dialect : void 0;
		this.dialectConfig = (0, __entity_ts.is)(dialect, __mssql_core_dialect_ts.MsSqlDialect) ? void 0 : dialect;
	}
	$with(alias) {
		const queryBuilder = this;
		return { as(qb) {
			if (typeof qb === "function") qb = qb(queryBuilder);
			return new Proxy(new __subquery_ts.WithSubquery(qb.getSQL(), qb.getSelectedFields(), alias, true), new __selection_proxy_ts.SelectionProxyHandler({
				alias,
				sqlAliasedBehavior: "alias",
				sqlBehavior: "error"
			}));
		} };
	}
	with(...queries) {
		const self = this;
		function select(fields) {
			return new require_mssql_core_query_builders_select.MsSqlSelectBuilder({
				fields: fields ?? void 0,
				session: void 0,
				dialect: self.getDialect(),
				withList: queries
			});
		}
		function selectDistinct(fields) {
			return new require_mssql_core_query_builders_select.MsSqlSelectBuilder({
				fields: fields ?? void 0,
				session: void 0,
				dialect: self.getDialect(),
				withList: queries,
				distinct: true
			});
		}
		return {
			select,
			selectDistinct
		};
	}
	select(fields) {
		return new require_mssql_core_query_builders_select.MsSqlSelectBuilder({
			fields: fields ?? void 0,
			session: void 0,
			dialect: this.getDialect()
		});
	}
	selectDistinct(fields) {
		return new require_mssql_core_query_builders_select.MsSqlSelectBuilder({
			fields: fields ?? void 0,
			session: void 0,
			dialect: this.getDialect(),
			distinct: true
		});
	}
	getDialect() {
		if (!this.dialect) this.dialect = new __mssql_core_dialect_ts.MsSqlDialect(this.dialectConfig);
		return this.dialect;
	}
};

//#endregion
exports.QueryBuilder = QueryBuilder;
//# sourceMappingURL=query-builder.cjs.map