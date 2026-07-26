import { entityKind } from "../../entity.js";
import { applyMixins } from "../../utils.js";
import { SQL, sql } from "../../sql/sql.js";
import { QueryPromise } from "../../query-promise.js";

//#region src/cockroach-core/query-builders/count.ts
var CockroachCountBuilder = class CockroachCountBuilder extends SQL {
	static [entityKind] = "CockroachCountBuilder";
	dialect;
	session;
	static buildCount(source, filters, parens) {
		const query = sql`select count(*) from ${source}${sql` where ${filters}`.if(filters)}`;
		return parens ? sql`(${query})` : query;
	}
	constructor(countConfig) {
		super(CockroachCountBuilder.buildCount(countConfig.source, countConfig.filters, true).queryChunks);
		this.countConfig = countConfig;
		this.dialect = countConfig.dialect;
		this.session = countConfig.session;
		this.mapWith((e) => {
			if (typeof e === "number") return e;
			return Number(e ?? 0);
		});
	}
	executableSql;
	build() {
		if (!this.executableSql) {
			const { source, filters } = this.countConfig;
			this.executableSql = CockroachCountBuilder.buildCount(source, filters);
		}
		return this.dialect.sqlToQuery(this.executableSql);
	}
	execute(placeholderValues) {
		return this.session.prepareQuery(this.build(), void 0, void 0, (rows) => {
			const v = rows[0]?.[0];
			if (typeof v === "number") return v;
			return v ? Number(v) : 0;
		}).execute(placeholderValues);
	}
};
applyMixins(CockroachCountBuilder, [QueryPromise]);

//#endregion
export { CockroachCountBuilder };
//# sourceMappingURL=count.js.map