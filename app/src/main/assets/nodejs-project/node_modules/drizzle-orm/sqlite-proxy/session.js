import { entityKind } from "../entity.js";
import { sql } from "../sql/sql.js";
import { NoopLogger } from "../logger.js";
import { NoopCache } from "../cache/core/index.js";
import { SQLiteAsyncPreparedQuery, SQLiteAsyncSession, SQLiteAsyncTransaction } from "../sqlite-core/async/session.js";

//#region src/sqlite-proxy/session.ts
var SQLiteRemoteSession = class extends SQLiteAsyncSession {
	static [entityKind] = "SQLiteRemoteSession";
	logger;
	cache;
	constructor(client, dialect, relations, batchClient, options = {}) {
		super(dialect, "async");
		this.client = client;
		this.relations = relations;
		this.batchClient = batchClient;
		this.options = options;
		this.logger = options.logger ?? new NoopLogger();
		this.cache = options.cache ?? new NoopCache();
	}
	prepareQuery(query, mode, _prepare, executeMethod, mapper, queryMetadata, cacheConfig) {
		return new SQLiteAsyncPreparedQuery("async", executeMethod, {
			all: (params) => this.client(query.sql, params, "all").then(({ rows }) => rows),
			get: (params) => this.client(query.sql, params, "get").then(({ rows }) => rows),
			run: (params) => this.client(query.sql, params, "run"),
			values: (params) => this.client(query.sql, params, "all").then(({ rows }) => rows)
		}, query, mapper, mode, this.logger, this.cache, queryMetadata, cacheConfig);
	}
	objects(_query) {
		throw new Error("Proxy driver doesn't support object-mode querying");
	}
	object(_query) {
		throw new Error("Proxy driver doesn't support object-mode querying");
	}
	async batch(queries) {
		const preparedQueries = [];
		const builtQueries = [];
		for (const query of queries) {
			const preparedQuery = query._prepare();
			const builtQuery = preparedQuery.getQuery();
			preparedQueries.push(preparedQuery);
			builtQueries.push({
				sql: builtQuery.sql,
				params: builtQuery.params,
				method: preparedQuery.executeMethod
			});
		}
		return (await this.batchClient(builtQueries)).map((result, i) => {
			const { executeMethod, mapper } = preparedQueries[i];
			if (executeMethod === "run") return result;
			if (executeMethod === "values") return result.rows;
			const { rows } = result;
			if (executeMethod === "get") {
				if (!rows) return;
				if (!mapper) return rows;
				return mapper([rows])[0];
			}
			if (!mapper) return rows;
			return mapper(rows);
		});
	}
	async transaction(transaction, config) {
		const tx = new SQLiteProxyTransaction("async", this.dialect, this, this.relations);
		await this.run(sql.raw(`begin${config?.behavior ? " " + config.behavior : ""}`));
		try {
			const result = await transaction(tx);
			await this.run(sql`commit`);
			return result;
		} catch (err) {
			await this.run(sql`rollback`);
			throw err;
		}
	}
};
var SQLiteProxyTransaction = class SQLiteProxyTransaction extends SQLiteAsyncTransaction {
	static [entityKind] = "SQLiteProxyTransaction";
	async transaction(transaction) {
		const savepointName = `sp${this.nestedIndex}`;
		const tx = new SQLiteProxyTransaction("async", this.dialect, this.session, this._.relations, this.nestedIndex + 1);
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

//#endregion
export { SQLiteProxyTransaction, SQLiteRemoteSession };
//# sourceMappingURL=session.js.map