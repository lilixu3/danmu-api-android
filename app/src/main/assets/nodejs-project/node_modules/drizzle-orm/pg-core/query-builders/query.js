import { entityKind } from "../../entity.js";

//#region src/pg-core/query-builders/query.ts
var RelationalQueryBuilder = class {
	static [entityKind] = "PgRelationalQueryBuilderV2";
	constructor(schema, table, tableConfig, dialect, session, parseJson, builder = PgRelationalQuery) {
		this.schema = schema;
		this.table = table;
		this.tableConfig = tableConfig;
		this.dialect = dialect;
		this.session = session;
		this.parseJson = parseJson;
		this.builder = builder;
	}
	findMany(config) {
		return new this.builder(this.schema, this.table, this.tableConfig, this.dialect, this.session, config ?? true, "many", this.parseJson);
	}
	findFirst(config) {
		return new this.builder(this.schema, this.table, this.tableConfig, this.dialect, this.session, config ?? true, "first", this.parseJson);
	}
};
var PgRelationalQuery = class {
	static [entityKind] = "PgRelationalQueryV2";
	constructor(schema, table, tableConfig, dialect, session, config, mode, parseJson) {
		this.schema = schema;
		this.table = table;
		this.tableConfig = tableConfig;
		this.dialect = dialect;
		this.session = session;
		this.config = config;
		this.mode = mode;
		this.parseJson = parseJson;
	}
	_getQuery() {
		return this.dialect.buildRelationalQuery({
			schema: this.schema,
			table: this.table,
			tableConfig: this.tableConfig,
			queryConfig: this.config,
			mode: this.mode
		});
	}
	getSQL() {
		return this._getQuery().sql;
	}
	_toSQL() {
		const query = this._getQuery();
		return {
			query,
			builtQuery: this.dialect.sqlToQuery(query.sql)
		};
	}
	toSQL() {
		return this._toSQL().builtQuery;
	}
};

//#endregion
export { PgRelationalQuery, RelationalQueryBuilder };
//# sourceMappingURL=query.js.map