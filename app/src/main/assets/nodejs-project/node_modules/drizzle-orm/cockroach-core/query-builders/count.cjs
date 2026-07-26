Object.defineProperty(exports, Symbol.toStringTag, { value: 'Module' });
const require_runtime = require('../../_virtual/_rolldown/runtime.cjs');
let __entity_ts = require("../../entity.cjs");
let __utils_ts = require("../../utils.cjs");
let __sql_sql_ts = require("../../sql/sql.cjs");
let __query_promise_ts = require("../../query-promise.cjs");

//#region src/cockroach-core/query-builders/count.ts
var CockroachCountBuilder = class CockroachCountBuilder extends __sql_sql_ts.SQL {
	static [__entity_ts.entityKind] = "CockroachCountBuilder";
	dialect;
	session;
	static buildCount(source, filters, parens) {
		const query = __sql_sql_ts.sql`select count(*) from ${source}${__sql_sql_ts.sql` where ${filters}`.if(filters)}`;
		return parens ? __sql_sql_ts.sql`(${query})` : query;
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
(0, __utils_ts.applyMixins)(CockroachCountBuilder, [__query_promise_ts.QueryPromise]);

//#endregion
exports.CockroachCountBuilder = CockroachCountBuilder;
//# sourceMappingURL=count.cjs.map