Object.defineProperty(exports, Symbol.toStringTag, { value: 'Module' });
const require_runtime = require('../_virtual/_rolldown/runtime.cjs');
let __entity_ts = require("../entity.cjs");
let __logger_ts = require("../logger.cjs");
let __cache_core_index_ts = require("../cache/core/index.cjs");
let __pg_core_async_session_ts = require("../pg-core/async/session.cjs");

//#region src/xata-http/session.ts
var XataHttpSession = class extends __pg_core_async_session_ts.PgAsyncSession {
	static [__entity_ts.entityKind] = "XataHttpSession";
	logger;
	cache;
	constructor(client, dialect, relations, options = {}) {
		super(dialect);
		this.client = client;
		this.relations = relations;
		this.options = options;
		this.logger = options.logger ?? new __logger_ts.NoopLogger();
		this.cache = options.cache ?? new __cache_core_index_ts.NoopCache();
	}
	prepareQuery(query, mode, _name, mapper, queryMetadata, cacheConfig) {
		const executor = async (params) => {
			if (mode === "raw") return this.client.sql({
				statement: query.sql,
				params
			});
			if (mode === "objects") return this.client.sql({
				statement: query.sql,
				params,
				responseType: "json"
			}).then(({ warning, records }) => {
				if (warning) console.warn(warning);
				return records;
			});
			return this.client.sql({
				statement: query.sql,
				params,
				responseType: "array"
			}).then(({ warning, rows }) => {
				if (warning) console.warn(warning);
				return rows;
			});
		};
		return new __pg_core_async_session_ts.PgAsyncPreparedQuery(executor, query, mapper, mode, this.logger, this.cache, queryMetadata, cacheConfig);
	}
	async transaction(_transaction, _config = {}) {
		throw new Error("No transactions support in Xata Http driver");
	}
};

//#endregion
exports.XataHttpSession = XataHttpSession;
//# sourceMappingURL=session.cjs.map