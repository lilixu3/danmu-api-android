import { entityKind } from "../entity.js";
import { sql } from "../sql/sql.js";
import { NoopLogger } from "../logger.js";
import { NoopCache } from "../cache/core/index.js";
import { SQLiteAsyncPreparedQuery, SQLiteAsyncSession, SQLiteAsyncTransaction } from "../sqlite-core/async/session.js";

//#region src/libsql/session.ts
var LibSQLSession = class LibSQLSession extends SQLiteAsyncSession {
	static [entityKind] = "LibSQLSession";
	logger;
	cache;
	constructor(client, dialect, relations, options, tx) {
		super(dialect, "async");
		this.client = client;
		this.relations = relations;
		this.options = options;
		this.tx = tx;
		this.logger = options.logger ?? new NoopLogger();
		this.cache = options.cache ?? new NoopCache();
	}
	prepareQuery(query, mode, _prepare, executeMethod, mapper, queryMetadata, cacheConfig) {
		const client = this.tx ?? this.client;
		return new SQLiteAsyncPreparedQuery("async", executeMethod, {
			all: (params) => client.execute({
				sql: query.sql,
				args: params
			}).then(({ rows }) => mode === "arrays" ? rows.map(toArrayRow) : rows.map(normalizeRow)),
			get: (params) => client.execute({
				sql: query.sql,
				args: params
			}).then(({ rows }) => rows[0] ? mode === "arrays" ? toArrayRow(rows[0]) : normalizeRow(rows[0]) : void 0),
			run: (params) => client.execute({
				sql: query.sql,
				args: params
			}),
			values: (params) => client.execute({
				sql: query.sql,
				args: params
			}).then(({ rows }) => rows.map(toArrayRow))
		}, query, mapper, mode, this.logger, this.cache, queryMetadata, cacheConfig);
	}
	/** @internal */
	async batch(queries, isMigration) {
		const preparedQueries = [];
		const builtQueries = [];
		for (const query of queries) {
			const preparedQuery = query._prepare();
			const builtQuery = preparedQuery.getQuery();
			preparedQueries.push(preparedQuery);
			builtQueries.push({
				sql: builtQuery.sql,
				args: builtQuery.params
			});
		}
		return (await (isMigration ? this.client.migrate(builtQueries) : (this.tx ?? this.client).batch(builtQueries))).map((result, i) => {
			const { executeMethod, mapper, mode } = preparedQueries[i];
			if (executeMethod === "run") return result;
			if (executeMethod === "values") return result.rows;
			if (executeMethod === "get") {
				const value = result.rows[0];
				if (!value) return;
				const mapped = mode === "arrays" ? toArrayRow(value) : normalizeRow(value);
				if (!mapper) return mapped;
				return mapper([mapped])[0];
			}
			const { rows } = result;
			const mapped = mode === "arrays" ? rows.map(toArrayRow) : rows.map(normalizeRow);
			if (!mapper) return mapped;
			return mapper(mapped);
		});
	}
	async migrate(queries) {
		return this.batch(queries, true);
	}
	async transaction(transaction, _config) {
		const libsqlTx = await this.client.transaction();
		const session = new LibSQLSession(this.client, this.dialect, this.relations, this.options, libsqlTx);
		const tx = new LibSQLTransaction("async", this.dialect, session, this.relations);
		try {
			const result = await transaction(tx);
			await libsqlTx.commit();
			return result;
		} catch (err) {
			await libsqlTx.rollback();
			throw err;
		}
	}
};
var LibSQLTransaction = class LibSQLTransaction extends SQLiteAsyncTransaction {
	static [entityKind] = "LibSQLTransaction";
	async transaction(transaction) {
		const savepointName = `sp${this.nestedIndex}`;
		const tx = new LibSQLTransaction("async", this.dialect, this.session, this._.relations, this.nestedIndex + 1);
		await this.session.run(sql.raw(`savepoint ${savepointName}`));
		try {
			const result = await transaction(tx);
			await this.session.run(sql.raw(`release savepoint ${savepointName}`));
			return result;
		} catch (err) {
			await this.session.run(sql.raw(`rollback to savepoint ${savepointName}`));
			throw err;
		}
	}
};
function toArrayRow(obj) {
	return Array.prototype.slice.call(obj);
}
function normalizeRow(obj) {
	return Object.keys(obj).reduce((acc, key) => {
		if (Object.prototype.propertyIsEnumerable.call(obj, key)) acc[key] = obj[key];
		return acc;
	}, {});
}

//#endregion
export { LibSQLSession, LibSQLTransaction };
//# sourceMappingURL=session.js.map