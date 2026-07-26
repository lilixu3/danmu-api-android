import { MsSqlSelectBuilder } from "./select.js";
import { entityKind, is } from "../../entity.js";
import { WithSubquery } from "../../subquery.js";
import { SelectionProxyHandler } from "../../selection-proxy.js";
import { MsSqlDialect } from "../dialect.js";

//#region src/mssql-core/query-builders/query-builder.ts
var QueryBuilder = class {
	static [entityKind] = "MsSqlQueryBuilder";
	dialect;
	dialectConfig;
	constructor(dialect) {
		this.dialect = is(dialect, MsSqlDialect) ? dialect : void 0;
		this.dialectConfig = is(dialect, MsSqlDialect) ? void 0 : dialect;
	}
	$with(alias) {
		const queryBuilder = this;
		return { as(qb) {
			if (typeof qb === "function") qb = qb(queryBuilder);
			return new Proxy(new WithSubquery(qb.getSQL(), qb.getSelectedFields(), alias, true), new SelectionProxyHandler({
				alias,
				sqlAliasedBehavior: "alias",
				sqlBehavior: "error"
			}));
		} };
	}
	with(...queries) {
		const self = this;
		function select(fields) {
			return new MsSqlSelectBuilder({
				fields: fields ?? void 0,
				session: void 0,
				dialect: self.getDialect(),
				withList: queries
			});
		}
		function selectDistinct(fields) {
			return new MsSqlSelectBuilder({
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
		return new MsSqlSelectBuilder({
			fields: fields ?? void 0,
			session: void 0,
			dialect: this.getDialect()
		});
	}
	selectDistinct(fields) {
		return new MsSqlSelectBuilder({
			fields: fields ?? void 0,
			session: void 0,
			dialect: this.getDialect(),
			distinct: true
		});
	}
	getDialect() {
		if (!this.dialect) this.dialect = new MsSqlDialect(this.dialectConfig);
		return this.dialect;
	}
};

//#endregion
export { QueryBuilder };
//# sourceMappingURL=query-builder.js.map