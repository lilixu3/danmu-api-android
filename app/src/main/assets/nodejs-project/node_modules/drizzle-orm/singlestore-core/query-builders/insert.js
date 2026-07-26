import { extractUsedTable } from "../utils.js";
import { entityKind, is } from "../../entity.js";
import { Table } from "../../table.js";
import { mapUpdateSet, orderSelectedFields } from "../../utils.js";
import { Param, SQL, sql } from "../../sql/sql.js";
import { QueryPromise } from "../../query-promise.js";

//#region src/singlestore-core/query-builders/insert.ts
var SingleStoreInsertBuilder = class {
	static [entityKind] = "SingleStoreInsertBuilder";
	shouldIgnore = false;
	constructor(table, session, dialect) {
		this.table = table;
		this.session = session;
		this.dialect = dialect;
	}
	ignore() {
		this.shouldIgnore = true;
		return this;
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
		return new SingleStoreInsertBase(this.table, mappedValues, this.shouldIgnore, this.session, this.dialect);
	}
};
var SingleStoreInsertBase = class extends QueryPromise {
	static [entityKind] = "SingleStoreInsert";
	config;
	constructor(table, values, ignore, session, dialect) {
		super();
		this.session = session;
		this.dialect = dialect;
		this.config = {
			table,
			values,
			ignore
		};
	}
	/**
	* Adds an `on duplicate key update` clause to the query.
	*
	* Calling this method will update update the row if any unique index conflicts. MySQL will automatically determine the conflict target based on the primary key and unique indexes.
	*
	* See docs: {@link https://orm.drizzle.team/docs/insert#on-duplicate-key-update}
	*
	* @param config The `set` clause
	*
	* @example
	* ```ts
	* await db.insert(cars)
	*   .values({ id: 1, brand: 'BMW'})
	*   .onDuplicateKeyUpdate({ set: { brand: 'Porsche' }});
	* ```
	*
	* While MySQL does not directly support doing nothing on conflict, you can perform a no-op by setting any column's value to itself and achieve the same effect:
	*
	* ```ts
	* import { sql } from 'drizzle-orm';
	*
	* await db.insert(cars)
	*   .values({ id: 1, brand: 'BMW' })
	*   .onDuplicateKeyUpdate({ set: { id: sql`id` } });
	* ```
	*/
	onDuplicateKeyUpdate(config) {
		const setSql = this.dialect.buildUpdateSet(this.config.table, mapUpdateSet(this.config.table, config.set));
		this.config.onConflict = sql`update ${setSql}`;
		return this;
	}
	$returningId() {
		const returning = [];
		for (const [key, value] of Object.entries(this.config.table[Table.Symbol.Columns])) if (value.primary) returning.push({
			field: value,
			path: [key]
		});
		this.config.returning = orderSelectedFields(this.config.table[Table.Symbol.Columns]);
		return this;
	}
	/** @internal */
	getSQL() {
		return this.dialect.buildInsertQuery(this.config).sql;
	}
	toSQL() {
		return this.dialect.sqlToQuery(this.getSQL());
	}
	prepare() {
		const { sql, generatedIds } = this.dialect.buildInsertQuery(this.config);
		return this.session.prepareQuery(this.dialect.sqlToQuery(sql), void 0, void 0, generatedIds, this.config.returning, {
			type: "delete",
			tables: extractUsedTable(this.config.table)
		});
	}
	execute = (placeholderValues) => {
		return this.prepare().execute(placeholderValues);
	};
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
export { SingleStoreInsertBase, SingleStoreInsertBuilder };
//# sourceMappingURL=insert.js.map