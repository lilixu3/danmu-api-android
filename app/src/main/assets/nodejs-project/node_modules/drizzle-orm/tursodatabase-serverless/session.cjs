Object.defineProperty(exports, Symbol.toStringTag, { value: 'Module' });
const require_runtime = require('../_virtual/_rolldown/runtime.cjs');
let __entity_ts = require("../entity.cjs");
let __sql_sql_ts = require("../sql/sql.cjs");
let __logger_ts = require("../logger.cjs");
let __cache_core_index_ts = require("../cache/core/index.cjs");
let __sqlite_core_async_session_ts = require("../sqlite-core/async/session.cjs");

//#region src/tursodatabase-serverless/session.ts
var TursoDatabaseServerlessSession = class TursoDatabaseServerlessSession extends __sqlite_core_async_session_ts.SQLiteAsyncSession {
	static [__entity_ts.entityKind] = "TursoDatabaseServerlessSession";
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
	prepareQuery(query, mode, prepare, executeMethod, mapper, queryMetadata, cacheConfig) {
		let stmt;
		return new __sqlite_core_async_session_ts.SQLiteAsyncPreparedQuery("async", executeMethod, prepare ? {
			all: async (params) => {
				stmt ??= await this.client.prepare(query.sql);
				return stmt.raw(mode === "arrays").all(params);
			},
			get: async (params) => {
				stmt ??= await this.client.prepare(query.sql);
				return stmt.raw(mode === "arrays").get(params);
			},
			run: async (params) => {
				stmt ??= await this.client.prepare(query.sql);
				return stmt.run(params);
			},
			values: async (params) => {
				stmt ??= await this.client.prepare(query.sql);
				return stmt.raw(true).all(params);
			}
		} : {
			all: async (params) => {
				if (stmt || mode === "arrays") {
					stmt ??= await this.client.prepare(query.sql);
					return stmt.raw(mode === "arrays").all(params);
				}
				return this.client.all(query.sql, ...params);
			},
			get: async (params) => {
				if (stmt || mode === "arrays") {
					stmt ??= await this.client.prepare(query.sql);
					return stmt.raw(mode === "arrays").get(params);
				}
				return this.client.get(query.sql, ...params);
			},
			run: (params) => stmt ? stmt.run(params) : this.client.run(query.sql, ...params),
			values: async (params) => {
				stmt ??= await this.client.prepare(query.sql);
				return stmt.raw(true).all(params);
			}
		}, query, mapper, mode, this.logger, this.cache, queryMetadata, cacheConfig);
	}
	async transaction(transaction, _config) {
		const session = new TursoDatabaseServerlessSession(this.client, this.dialect, this.relations, this.options);
		const tx = new TursoDatabaseServerlessTransaction("async", this.dialect, session, this.relations);
		return await this.client.transaction(async () => await transaction(tx))();
	}
};
var TursoDatabaseServerlessTransaction = class TursoDatabaseServerlessTransaction extends __sqlite_core_async_session_ts.SQLiteAsyncTransaction {
	static [__entity_ts.entityKind] = "TursoDatabaseServerlessTransaction";
	async transaction(transaction) {
		const savepointName = `sp${this.nestedIndex}`;
		const tx = new TursoDatabaseServerlessTransaction("async", this.dialect, this.session, this._.relations, this.nestedIndex + 1);
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
exports.TursoDatabaseServerlessSession = TursoDatabaseServerlessSession;
exports.TursoDatabaseServerlessTransaction = TursoDatabaseServerlessTransaction;
//# sourceMappingURL=session.cjs.map