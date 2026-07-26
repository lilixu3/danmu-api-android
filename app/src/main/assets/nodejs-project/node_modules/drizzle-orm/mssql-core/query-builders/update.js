import { entityKind } from "../../entity.js";
import { Table } from "../../table.js";
import { mapUpdateSet, orderSelectedFields } from "../../utils.js";
import { QueryPromise } from "../../query-promise.js";

//#region src/mssql-core/query-builders/update.ts
var MsSqlUpdateBuilder = class {
	static [entityKind] = "MsSqlUpdateBuilder";
	constructor(table, session, dialect) {
		this.table = table;
		this.session = session;
		this.dialect = dialect;
	}
	set(values) {
		return new MsSqlUpdateBase(this.table, mapUpdateSet(this.table, values), this.session, this.dialect);
	}
};
var MsSqlUpdateBase = class extends QueryPromise {
	static [entityKind] = "MsSqlUpdate";
	config;
	constructor(table, set, session, dialect) {
		super();
		this.session = session;
		this.dialect = dialect;
		this.config = {
			set,
			table
		};
	}
	/**
	* Adds a 'where' clause to the query.
	*
	* Calling this method will update only those rows that fulfill a specified condition.
	*
	* See docs: {@link https://orm.drizzle.team/docs/update}
	*
	* @param where the 'where' clause.
	*
	* @example
	* You can use conditional operators and `sql function` to filter the rows to be updated.
	*
	* ```ts
	* // Update all cars with green color
	* db.update(cars).set({ color: 'red' })
	*   .where(eq(cars.color, 'green'));
	* // or
	* db.update(cars).set({ color: 'red' })
	*   .where(sql`${cars.color} = 'green'`)
	* ```
	*
	* You can logically combine conditional operators with `and()` and `or()` operators:
	*
	* ```ts
	* // Update all BMW cars with a green color
	* db.update(cars).set({ color: 'red' })
	*   .where(and(eq(cars.color, 'green'), eq(cars.brand, 'BMW')));
	*
	* // Update all cars with the green or blue color
	* db.update(cars).set({ color: 'red' })
	*   .where(or(eq(cars.color, 'green'), eq(cars.color, 'blue')));
	* ```
	*/
	where(where) {
		this.config.where = where;
		return this;
	}
	output(fields) {
		const columns = this.config.table[Table.Symbol.Columns];
		if (fields) {
			const output = {};
			if (fields.inserted) output.inserted = typeof fields.inserted === "boolean" ? orderSelectedFields(columns, ["inserted"]) : orderSelectedFields(fields.inserted, ["inserted"]);
			if (fields.deleted) output.deleted = typeof fields.deleted === "boolean" ? orderSelectedFields(columns, ["deleted"]) : orderSelectedFields(fields.deleted, ["deleted"]);
			this.config.output = output;
		} else this.config.output = { inserted: orderSelectedFields(columns) };
		return this;
	}
	/** @internal */
	getSQL() {
		return this.dialect.buildUpdateQuery(this.config);
	}
	toSQL() {
		return this.dialect.sqlToQuery(this.getSQL());
	}
	prepare() {
		const output = [...this.config.output?.inserted ?? [], ...this.config.output?.deleted ?? []];
		return this.session.prepareQuery(this.dialect.sqlToQuery(this.getSQL()), output.length ? output : void 0);
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
export { MsSqlUpdateBase, MsSqlUpdateBuilder };
//# sourceMappingURL=update.js.map