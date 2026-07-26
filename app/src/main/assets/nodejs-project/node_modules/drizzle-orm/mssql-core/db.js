import { MsSqlSelectBuilder } from "./query-builders/select.js";
import { QueryBuilder } from "./query-builders/query-builder.js";
import { MsSqlDeleteBase } from "./query-builders/delete.js";
import { MsSqlInsertBuilder } from "./query-builders/insert.js";
import { MsSqlUpdateBuilder } from "./query-builders/update.js";
import { RelationalQueryBuilder } from "./query-builders/query.js";
import { entityKind } from "../entity.js";
import { WithSubquery } from "../subquery.js";
import { sql } from "../sql/sql.js";
import { SelectionProxyHandler } from "../selection-proxy.js";

//#region src/mssql-core/db.ts
var MsSqlDatabase = class {
	static [entityKind] = "MsSqlDatabase";
	_query;
	constructor(dialect, session, schema) {
		this.dialect = dialect;
		this.session = session;
		this._ = schema ? {
			schema: schema.schema,
			tableNamesMap: schema.tableNamesMap
		} : {
			schema: void 0,
			tableNamesMap: {}
		};
		this._query = {};
		if (this._.schema) for (const [tableName, columns] of Object.entries(this._.schema)) this._query[tableName] = new RelationalQueryBuilder(schema.fullSchema, this._.schema, this._.tableNamesMap, schema.fullSchema[tableName], columns, dialect, session);
	}
	/**
	* Creates a subquery that defines a temporary named result set as a CTE.
	*
	* It is useful for breaking down complex queries into simpler parts and for reusing the result set in subsequent parts of the query.
	*
	* See docs: {@link https://orm.drizzle.team/docs/select#with-clause}
	*
	* @param alias The alias for the subquery.
	*
	* Failure to provide an alias will result in a DrizzleTypeError, preventing the subquery from being referenced in other queries.
	*
	* @example
	*
	* ```ts
	* // Create a subquery with alias 'sq' and use it in the select query
	* const sq = db.$with('sq').as(db.select().from(users).where(eq(users.id, 42)));
	*
	* const result = await db.with(sq).select().from(sq);
	* ```
	*
	* To select arbitrary SQL values as fields in a CTE and reference them in other CTEs or in the main query, you need to add aliases to them:
	*
	* ```ts
	* // Select an arbitrary SQL value as a field in a CTE and reference it in the main query
	* const sq = db.$with('sq').as(db.select({
	*   name: sql<string>`upper(${users.name})`.as('name'),
	* })
	* .from(users));
	*
	* const result = await db.with(sq).select({ name: sq.name }).from(sq);
	* ```
	*/
	$with(alias) {
		const self = this;
		return { as(qb) {
			if (typeof qb === "function") qb = qb(new QueryBuilder(self.dialect));
			return new Proxy(new WithSubquery(qb.getSQL(), qb.getSelectedFields(), alias, true), new SelectionProxyHandler({
				alias,
				sqlAliasedBehavior: "alias",
				sqlBehavior: "error"
			}));
		} };
	}
	/**
	* Incorporates a previously defined CTE (using `$with`) into the main query.
	*
	* This method allows the main query to reference a temporary named result set.
	*
	* See docs: {@link https://orm.drizzle.team/docs/select#with-clause}
	*
	* @param queries The CTEs to incorporate into the main query.
	*
	* @example
	*
	* ```ts
	* // Define a subquery 'sq' as a CTE using $with
	* const sq = db.$with('sq').as(db.select().from(users).where(eq(users.id, 42)));
	*
	* // Incorporate the CTE 'sq' into the main query and select from it
	* const result = await db.with(sq).select().from(sq);
	* ```
	*/
	with(...queries) {
		const self = this;
		function select(fields) {
			return new MsSqlSelectBuilder({
				fields: fields ?? void 0,
				session: self.session,
				dialect: self.dialect,
				withList: queries
			});
		}
		function selectDistinct(fields) {
			return new MsSqlSelectBuilder({
				fields: fields ?? void 0,
				session: self.session,
				dialect: self.dialect,
				withList: queries,
				distinct: true
			});
		}
		return {
			select,
			selectDistinct
		};
	}
	select(fields) {
		return new MsSqlSelectBuilder({
			fields: fields ?? void 0,
			session: this.session,
			dialect: this.dialect
		});
	}
	selectDistinct(fields) {
		return new MsSqlSelectBuilder({
			fields: fields ?? void 0,
			session: this.session,
			dialect: this.dialect,
			distinct: true
		});
	}
	/**
	* Creates an update query.
	*
	* Calling this method without `.where()` clause will update all rows in a table. The `.where()` clause specifies which rows should be updated.
	*
	* Use `.set()` method to specify which values to update.
	*
	* See docs: {@link https://orm.drizzle.team/docs/update}
	*
	* @param table The table to update.
	*
	* @example
	*
	* ```ts
	* // Update all rows in the 'cars' table
	* await db.update(cars).set({ color: 'red' });
	*
	* // Update rows with filters and conditions
	* await db.update(cars).set({ color: 'red' }).where(eq(cars.brand, 'BMW'));
	* ```
	*/
	update(table) {
		return new MsSqlUpdateBuilder(table, this.session, this.dialect);
	}
	/**
	* Creates an insert query.
	*
	* Calling this method will create new rows in a table. Use `.values()` method to specify which values to insert.
	*
	* See docs: {@link https://orm.drizzle.team/docs/insert}
	*
	* @param table The table to insert into.
	*
	* @example
	*
	* ```ts
	* // Insert one row
	* await db.insert(cars).values({ brand: 'BMW' });
	*
	* // Insert multiple rows
	* await db.insert(cars).values([{ brand: 'BMW' }, { brand: 'Porsche' }]);
	* ```
	*/
	insert(table) {
		return new MsSqlInsertBuilder(table, this.session, this.dialect);
	}
	/**
	* Creates a delete query.
	*
	* Calling this method without `.where()` clause will delete all rows in a table. The `.where()` clause specifies which rows should be deleted.
	*
	* See docs: {@link https://orm.drizzle.team/docs/delete}
	*
	* @param table The table to delete from.
	*
	* @example
	*
	* ```ts
	* // Delete all rows in the 'cars' table
	* await db.delete(cars);
	*
	* // Delete rows with filters and conditions
	* await db.delete(cars).where(eq(cars.color, 'green'));
	* ```
	*/
	delete(table) {
		return new MsSqlDeleteBase(table, this.session, this.dialect);
	}
	execute(query) {
		return this.session.execute((typeof query === "string" ? sql.raw(query) : query).getSQL());
	}
	transaction(transaction, config) {
		return this.session.transaction(transaction, config);
	}
};
const withReplicas = (primary, replicas, getReplica = () => replicas[Math.floor(Math.random() * replicas.length)]) => {
	const select = (...args) => getReplica(replicas).select(...args);
	const selectDistinct = (...args) => getReplica(replicas).selectDistinct(...args);
	const $with = (...args) => getReplica(replicas).with(...args);
	const update = (...args) => primary.update(...args);
	const insert = (...args) => primary.insert(...args);
	const $delete = (...args) => primary.delete(...args);
	const execute = (...args) => primary.execute(...args);
	const transaction = (...args) => primary.transaction(...args);
	return {
		...primary,
		update,
		insert,
		delete: $delete,
		execute,
		transaction,
		$primary: primary,
		$replicas: replicas,
		select,
		selectDistinct,
		with: $with,
		get _query() {
			return getReplica(replicas)._query;
		}
	};
};

//#endregion
export { MsSqlDatabase, withReplicas };
//# sourceMappingURL=db.js.map