import { entityKind } from "../../entity.js";
import { SQL, sql } from "../../sql/sql.js";

//#region src/pg-core/query-builders/count.ts
var PgCountBuilder = class PgCountBuilder extends SQL {
	static [entityKind] = "PgCountBuilder";
	dialect;
	static buildCount(source, filters, parens) {
		const query = sql`select count(*) from ${source}${sql` where ${filters}`.if(filters)}`;
		return parens ? sql`(${query})` : query;
	}
	constructor(countConfig) {
		super(PgCountBuilder.buildCount(countConfig.source, countConfig.filters, true).queryChunks);
		this.countConfig = countConfig;
		this.dialect = countConfig.dialect;
		this.mapWith((e) => {
			if (typeof e === "number") return e;
			return Number(e ?? 0);
		});
	}
	executableSql;
	build() {
		if (!this.executableSql) {
			const { source, filters } = this.countConfig;
			this.executableSql = PgCountBuilder.buildCount(source, filters);
		}
		return this.dialect.sqlToQuery(this.executableSql);
	}
};

//#endregion
export { PgCountBuilder };
//# sourceMappingURL=count.js.map