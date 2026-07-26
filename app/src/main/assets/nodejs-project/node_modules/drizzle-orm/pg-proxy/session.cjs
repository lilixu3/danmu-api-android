Object.defineProperty(exports, Symbol.toStringTag, { value: 'Module' });
const require_runtime = require('../_virtual/_rolldown/runtime.cjs');
let __entity_ts = require("../entity.cjs");
let __logger_ts = require("../logger.cjs");
let __pg_core_async_session_ts = require("../pg-core/async/session.cjs");
let __cache_core_cache_ts = require("../cache/core/cache.cjs");

//#region src/pg-proxy/session.ts
var PgRemoteSession = class extends __pg_core_async_session_ts.PgAsyncSession {
	static [__entity_ts.entityKind] = "PgRemoteSession";
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
			if (mode === "arrays") return this.client(query.sql, params, "all").then((r) => r.rows);
			return this.client(query.sql, params, "execute").then((r) => r.rows);
		};
		return new __pg_core_async_session_ts.PgAsyncPreparedQuery(executor, query, mapper, mode, this.logger, this.cache, queryMetadata, cacheConfig);
	}
	async transaction(_transaction, _config) {
		throw new Error("Transactions are not supported by the Postgres Proxy driver");
	}
};

//#endregion
exports.PgRemoteSession = PgRemoteSession;
//# sourceMappingURL=session.cjs.map