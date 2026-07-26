Object.defineProperty(exports, Symbol.toStringTag, { value: 'Module' });
const require_runtime = require('../_virtual/_rolldown/runtime.cjs');
let __entity_ts = require("../entity.cjs");
let __sql_sql_ts = require("../sql/sql.cjs");
let __logger_ts = require("../logger.cjs");
let __cache_core_index_ts = require("../cache/core/index.cjs");
let __sqlite_core_async_session_ts = require("../sqlite-core/async/session.cjs");

//#region src/sqlite-proxy/session.ts
var SQLiteRemoteSession = class extends __sqlite_core_async_session_ts.SQLiteAsyncSession {
	static [__entity_ts.entityKind] = "SQLiteRemoteSession";
	logger;
	cache;
	constructor(client, dialect, relations, batchClient, options = {}) {
		super(dialect, "async");
		this.client = client;
		this.relations = relations;
		this.batchClient = batchClient;
		this.options = options;
		this.logger = options.logger ?? new __logger_ts.NoopLogger();
		this.cache = options.cache ?? new __cache_core_index_ts.NoopCache();
	}
	prepareQuery(query, mode, _prepare, executeMethod, mapper, queryMetadata, cacheConfig) {
		return new __sqlite_core_async_session_ts.SQLiteAsyncPreparedQuery("async", executeMethod, {
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
		await this.run(__sql_sql_ts.sql.raw(`begin${config?.behavior ? " " + config.behavior : ""}`));
		try {
			const result = await transaction(tx);
			await this.run(__sql_sql_ts.sql`commit`);
			return result;
		} catch (err) {
			await this.run(__sql_sql_ts.sql`rollback`);
			throw err;
		}
	}
};
var SQLiteProxyTransaction = class SQLiteProxyTransaction extends __sqlite_core_async_session_ts.SQLiteAsyncTransaction {
	static [__entity_ts.entityKind] = "SQLiteProxyTransaction";
	async transaction(transaction) {
		const savepointName = `sp${this.nestedIndex}`;
		const tx = new SQLiteProxyTransaction("async", this.dialect, this.session, this._.relations, this.nestedIndex + 1);
		await this.session.run(__sql_sql_ts.sql.raw(`savepoint ${savepointName}`));
		try {
			const result = await transaction(tx);
			await this.session.run(__sql_sql_ts.sql.raw(`release savepoint ${savepointName}`));
			return result;
		} catch (err) {
			await this.session.run(__sql_sql_ts.sql.raw(`rollback to savepoint ${savepointName}`));
			throw err;
		}
	}
};

//#endregion
exports.SQLiteProxyTransaction = SQLiteProxyTransaction;
exports.SQLiteRemoteSession = SQLiteRemoteSession;
//# sourceMappingURL=session.cjs.map