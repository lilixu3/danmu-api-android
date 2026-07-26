Object.defineProperty(exports, Symbol.toStringTag, { value: 'Module' });
const require_runtime = require('../_virtual/_rolldown/runtime.cjs');
let __entity_ts = require("../entity.cjs");
let __sql_sql_ts = require("../sql/sql.cjs");
let __logger_ts = require("../logger.cjs");
let __cache_core_index_ts = require("../cache/core/index.cjs");
let __sqlite_core_async_session_ts = require("../sqlite-core/async/session.cjs");

//#region src/op-sqlite/session.ts
var OPSQLiteSession = class extends __sqlite_core_async_session_ts.SQLiteAsyncSession {
	static [__entity_ts.entityKind] = "OPSQLiteSession";
	logger;
	cache;
	constructor(client, dialect, relations, options = {}) {
		super(dialect, "async");
		this.client = client;
		this.relations = relations;
		this.options = options;
		this.logger = options.logger ?? new __logger_ts.NoopLogger();
		this.cache = options.cache ?? new __cache_core_index_ts.NoopCache();
	}
	prepareQuery(query, mode, _prepare, executeMethod, mapper, queryMetadata, cacheConfig) {
		return new __sqlite_core_async_session_ts.SQLiteAsyncPreparedQuery("async", executeMethod, {
			all: (params) => {
				if (mode === "arrays") return this.client.executeRawAsync(query.sql, params);
				return this.client.executeAsync(query.sql, params).then(({ rows }) => rows?._array || []);
			},
			get: (params) => {
				if (mode === "arrays") return this.client.executeRawAsync(query.sql, params).then((rows) => rows[0]);
				return this.client.executeAsync(query.sql, params).then(({ rows }) => rows?._array?.[0]);
			},
			run: (params) => {
				return this.client.executeAsync(query.sql, params);
			},
			values: (params) => {
				return this.client.executeRawAsync(query.sql, params);
			}
		}, query, mapper, mode, this.logger, this.cache, queryMetadata, cacheConfig);
	}
	transaction(transaction, config = {}) {
		const tx = new OPSQLiteTransaction("async", this.dialect, this, this.relations);
		this.run(__sql_sql_ts.sql.raw(`begin${config?.behavior ? " " + config.behavior : ""}`));
		try {
			const result = transaction(tx);
			this.run(__sql_sql_ts.sql`commit`);
			return result;
		} catch (err) {
			this.run(__sql_sql_ts.sql`rollback`);
			throw err;
		}
	}
};
var OPSQLiteTransaction = class OPSQLiteTransaction extends __sqlite_core_async_session_ts.SQLiteAsyncTransaction {
	static [__entity_ts.entityKind] = "OPSQLiteTransaction";
	transaction(transaction) {
		const savepointName = `sp${this.nestedIndex}`;
		const tx = new OPSQLiteTransaction("async", this.dialect, this.session, this._.relations, this.nestedIndex + 1);
		this.session.run(__sql_sql_ts.sql.raw(`savepoint ${savepointName}`));
		try {
			const result = transaction(tx);
			this.session.run(__sql_sql_ts.sql.raw(`release savepoint ${savepointName}`));
			return result;
		} catch (err) {
			this.session.run(__sql_sql_ts.sql.raw(`rollback to savepoint ${savepointName}`));
			throw err;
		}
	}
};

//#endregion
exports.OPSQLiteSession = OPSQLiteSession;
exports.OPSQLiteTransaction = OPSQLiteTransaction;
//# sourceMappingURL=session.cjs.map