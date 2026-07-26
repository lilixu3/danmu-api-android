Object.defineProperty(exports, Symbol.toStringTag, { value: 'Module' });
const require_runtime = require('../../_virtual/_rolldown/runtime.cjs');
let __entity_ts = require("../../entity.cjs");
let __table_ts = require("../../table.cjs");
let __utils_ts = require("../../utils.cjs");
let __sql_sql_ts = require("../../sql/sql.cjs");
let __query_promise_ts = require("../../query-promise.cjs");

//#region src/mssql-core/query-builders/insert.ts
var MsSqlInsertBuilder = class {
	static [__entity_ts.entityKind] = "MsSqlInsertBuilder";
	config;
	table;
	session;
	dialect;
	constructor(table, session, dialect, output) {
		this.table = table;
		this.session = session;
		this.dialect = dialect;
		this.config = {
			table,
			output
		};
	}
	values(values) {
		values = Array.isArray(values) ? values : [values];
		if (values.length === 0) throw new Error("values() must be called with at least one value");
		const mappedValues = values.map((entry) => {
			const result = {};
			const cols = this.table[__table_ts.Table.Symbol.Columns];
			for (const colKey of Object.keys(entry)) {
				const colValue = entry[colKey];
				result[colKey] = (0, __entity_ts.is)(colValue, __sql_sql_ts.SQL) ? colValue : new __sql_sql_ts.Param(colValue, cols[colKey]);
			}
			return result;
		});
		return new MsSqlInsertBase(this.table, mappedValues, this.session, this.dialect, this.config.output);
	}
	output(fields = this.table[__table_ts.Table.Symbol.Columns]) {
		this.config.output = (0, __utils_ts.orderSelectedFields)(fields);
		return this;
	}
};
var MsSqlInsertBase = class extends __query_promise_ts.QueryPromise {
	static [__entity_ts.entityKind] = "MsSqlInsert";
	config;
	constructor(table, values, session, dialect, output) {
		super();
		this.session = session;
		this.dialect = dialect;
		this.config = {
			table,
			values,
			output
		};
	}
	/** @internal */
	getSQL() {
		return this.dialect.buildInsertQuery(this.config);
	}
	toSQL() {
		return this.dialect.sqlToQuery(this.getSQL());
	}
	prepare() {
		return this.session.prepareQuery(this.dialect.sqlToQuery(this.getSQL()), this.config.output);
	}
	execute(placeholderValues) {
		return this.prepare().execute(placeholderValues);
	}
	createIterator = () => {
		const self = this;
		return async function* (placeholderValues) {
			yield* self.prepare().iterator(placeholderValues);
		};
	};
	iterator = this.createIterator();
};

//#endregion
exports.MsSqlInsertBase = MsSqlInsertBase;
exports.MsSqlInsertBuilder = MsSqlInsertBuilder;
//# sourceMappingURL=insert.cjs.map