Object.defineProperty(exports, Symbol.toStringTag, { value: 'Module' });
const require_runtime = require('../../_virtual/_rolldown/runtime.cjs');
let __entity_ts = require("../../entity.cjs");
let __logger_ts = require("../../logger.cjs");
let __cache_core_index_ts = require("../../cache/core/index.cjs");
let __sqlite_core_async_session_ts = require("../../sqlite-core/async/session.cjs");

//#region src/bun-sql/sqlite/session.ts
var BunSQLiteSession = class BunSQLiteSession extends __sqlite_core_async_session_ts.SQLiteAsyncSession {
	static [__entity_ts.entityKind] = "BunSQLiteSession";
	logger;
	cache;
	constructor(client, dialect, relations, options) {
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
				const q = this.client.unsafe(query.sql, params);
				if (mode === "arrays") return q.values();
				return q;
			},
			get: (params) => {
				const q = this.client.unsafe(query.sql, params);
				if (mode === "arrays") return q.values().then((rows) => rows[0]);
				return q.then((rows) => rows[0]);
			},
			run: (params) => {
				return this.client.unsafe(query.sql, params);
			},
			values: (params) => {
				return this.client.unsafe(query.sql, params).values();
			}
		}, query, mapper, mode, this.logger, this.cache, queryMetadata, cacheConfig);
	}
	async transaction(transaction, config) {
		return this.client.begin(config?.behavior ?? "", async (client) => {
			const session = new BunSQLiteSession(client, this.dialect, this.relations, this.options);
			return await transaction(new BunSQLiteTransaction("async", this.dialect, session, this.relations));
		});
	}
};
var BunSQLiteTransaction = class BunSQLiteTransaction extends __sqlite_core_async_session_ts.SQLiteAsyncTransaction {
	static [__entity_ts.entityKind] = "BunSQLiteTransaction";
	async transaction(transaction) {
		return this.session.client.savepoint(async (client) => {
			const session = new BunSQLiteSession(client, this.session.dialect, this._.relations, this.session.options);
			return await transaction(new BunSQLiteTransaction("async", this.dialect, session, this._.relations, this.nestedIndex + 1));
		});
	}
};

//#endregion
exports.BunSQLiteSession = BunSQLiteSession;
exports.BunSQLiteTransaction = BunSQLiteTransaction;
//# sourceMappingURL=session.cjs.map