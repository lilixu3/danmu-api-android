import { entityKind, is } from "../../entity.js";
import { Table } from "../../table.js";
import { orderSelectedFields } from "../../utils.js";
import { Param, SQL } from "../../sql/sql.js";
import { QueryPromise } from "../../query-promise.js";

//#region src/mssql-core/query-builders/insert.ts
var MsSqlInsertBuilder = class {
	static [entityKind] = "MsSqlInsertBuilder";
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
			const cols = this.table[Table.Symbol.Columns];
			for (const colKey of Object.keys(entry)) {
				const colValue = entry[colKey];
				result[colKey] = is(colValue, SQL) ? colValue : new Param(colValue, cols[colKey]);
			}
			return result;
		});
		return new MsSqlInsertBase(this.table, mappedValues, this.session, this.dialect, this.config.output);
	}
	output(fields = this.table[Table.Symbol.Columns]) {
		this.config.output = orderSelectedFields(fields);
		return this;
	}
};
var MsSqlInsertBase = class extends QueryPromise {
	static [entityKind] = "MsSqlInsert";
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
export { MsSqlInsertBase, MsSqlInsertBuilder };
//# sourceMappingURL=insert.js.map