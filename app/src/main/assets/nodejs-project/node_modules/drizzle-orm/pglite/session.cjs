Object.defineProperty(exports, Symbol.toStringTag, { value: 'Module' });
const require_runtime = require('../_virtual/_rolldown/runtime.cjs');
let __entity_ts = require("../entity.cjs");
let __sql_sql_ts = require("../sql/sql.cjs");
let __logger_ts = require("../logger.cjs");
let __pg_core_async_session_ts = require("../pg-core/async/session.cjs");
let __cache_core_cache_ts = require("../cache/core/cache.cjs");
let _electric_sql_pglite = require("@electric-sql/pglite");

//#region src/pglite/session.ts
const parsers = {
	[_electric_sql_pglite.types.TIMESTAMP]: (value) => value,
	[_electric_sql_pglite.types.TIMESTAMPTZ]: (value) => value,
	[_electric_sql_pglite.types.INTERVAL]: (value) => value,
	[_electric_sql_pglite.types.DATE]: (value) => value,
	[1231]: (value) => value,
	[1115]: (value) => value,
	[1185]: (value) => value,
	[1187]: (value) => value,
	[1182]: (value) => value
};
var PgliteSession = class PgliteSession extends __pg_core_async_session_ts.PgAsyncSession {
	static [__entity_ts.entityKind] = "PgliteSession";
	logger;
	cache;
	constructor(client, dialect, relations, options = {}) {
		super(dialect);
		this.client = client;
		this.relations = relations;
		this.options = options;
		this.logger = options.logger ?? new __logger_ts.NoopLogger();
		this.cache = options.cache ?? new __cache_core_cache_ts.NoopCache();
	}
	prepareQuery(query, mode, _name, mapper, queryMetadata, cacheConfig) {
		const executor = async (params) => {
			return this.client.query(query.sql, params, {
				rowMode: mode === "arrays" ? "array" : "object",
				parsers
			}).then((r) => mode === "raw" ? r : r.rows);
		};
		return new __pg_core_async_session_ts.PgAsyncPreparedQuery(executor, query, mapper, mode, this.logger, this.cache, queryMetadata, cacheConfig);
	}
	async transaction(transaction, config) {
		return this.client.transaction(async (client) => {
			const session = new PgliteSession(client, this.dialect, this.relations, this.options);
			const tx = new PgliteTransaction(this.dialect, session, this.relations, void 0, false);
			if (config) await tx.setTransaction(config);
			return transaction(tx);
		});
	}
};
var PgliteTransaction = class PgliteTransaction extends __pg_core_async_session_ts.PgAsyncTransaction {
	static [__entity_ts.entityKind] = "PgliteTransaction";
	async transaction(transaction) {
		const savepointName = `sp${this.nestedIndex + 1}`;
		const tx = new PgliteTransaction(this.dialect, this.session, this._.relations, this.nestedIndex + 1, false);
		await tx.execute(__sql_sql_ts.sql.raw(`savepoint ${savepointName}`));
		try {
			const result = await transaction(tx);
			await tx.execute(__sql_sql_ts.sql.raw(`release savepoint ${savepointName}`));
			return result;
		} catch (err) {
			await tx.execute(__sql_sql_ts.sql.raw(`rollback to savepoint ${savepointName}`));
			throw err;
		}
	}
};

//#endregion
exports.PgliteSession = PgliteSession;
exports.PgliteTransaction = PgliteTransaction;
//# sourceMappingURL=session.cjs.map