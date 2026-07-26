Object.defineProperty(exports, Symbol.toStringTag, { value: 'Module' });
const require_runtime = require('../../_virtual/_rolldown/runtime.cjs');
let __entity_ts = require("../../entity.cjs");
let __table_ts = require("../../table.cjs");
let __utils_ts = require("../../utils.cjs");
let __query_promise_ts = require("../../query-promise.cjs");

//#region src/mssql-core/query-builders/delete.ts
var MsSqlDeleteBase = class extends __query_promise_ts.QueryPromise {
	static [__entity_ts.entityKind] = "MsSqlDelete";
	config;
	constructor(table, session, dialect) {
		super();
		this.table = table;
		this.session = session;
		this.dialect = dialect;
		this.config = { table };
	}
	/**
	* Adds a `where` clause to the query.
	*
	* Calling this method will delete only those rows that fulfill a specified condition.
	*
	* See docs: {@link https://orm.drizzle.team/docs/delete}
	*
	* @param where the `where` clause.
	*
	* @example
	* You can use conditional operators and `sql function` to filter the rows to be deleted.
	*
	* ```ts
	* // Delete all cars with green color
	* db.delete(cars).where(eq(cars.color, 'green'));
	* // or
	* db.delete(cars).where(sql`${cars.color} = 'green'`)
	* ```
	*
	* You can logically combine conditional operators with `and()` and `or()` operators:
	*
	* ```ts
	* // Delete all BMW cars with a green color
	* db.delete(cars).where(and(eq(cars.color, 'green'), eq(cars.brand, 'BMW')));
	*
	* // Delete all cars with the green or blue color
	* db.delete(cars).where(or(eq(cars.color, 'green'), eq(cars.color, 'blue')));
	* ```
	*/
	where(where) {
		this.config.where = where;
		return this;
	}
	output(fields = this.config.table[__table_ts.Table.Symbol.Columns]) {
		this.config.output = (0, __utils_ts.orderSelectedFields)(fields);
		return this;
	}
	/** @internal */
	getSQL() {
		return this.dialect.buildDeleteQuery(this.config);
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
	$dynamic() {
		return this;
	}
};

//#endregion
exports.MsSqlDeleteBase = MsSqlDeleteBase;
//# sourceMappingURL=delete.cjs.map