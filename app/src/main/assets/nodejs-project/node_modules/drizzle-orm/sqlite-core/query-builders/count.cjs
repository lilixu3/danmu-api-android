Object.defineProperty(exports, Symbol.toStringTag, { value: 'Module' });
const require_runtime = require('../../_virtual/_rolldown/runtime.cjs');
let __entity_ts = require("../../entity.cjs");
let __sql_sql_ts = require("../../sql/sql.cjs");

//#region src/sqlite-core/query-builders/count.ts
var SQLiteCountBuilder = class SQLiteCountBuilder extends __sql_sql_ts.SQL {
	static [__entity_ts.entityKind] = "SQLiteCountBuilder";
	dialect;
	session;
	static buildCount(source, filters, parens) {
		const query = __sql_sql_ts.sql`select count(*) from ${source}${__sql_sql_ts.sql` where ${filters}`.if(filters)}`;
		return parens ? __sql_sql_ts.sql`(${query})` : query;
	}
	constructor(countConfig) {
		super(SQLiteCountBuilder.buildCount(countConfig.source, countConfig.filters, true).queryChunks);
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
			this.executableSql = SQLiteCountBuilder.buildCount(source, filters);
		}
		return this.dialect.sqlToQuery(this.executableSql);
	}
};

//#endregion
exports.SQLiteCountBuilder = SQLiteCountBuilder;
//# sourceMappingURL=count.cjs.map