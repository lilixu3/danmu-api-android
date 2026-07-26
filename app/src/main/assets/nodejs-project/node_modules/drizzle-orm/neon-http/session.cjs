Object.defineProperty(exports, Symbol.toStringTag, { value: 'Module' });
const require_runtime = require('../_virtual/_rolldown/runtime.cjs');
let __entity_ts = require("../entity.cjs");
let __logger_ts = require("../logger.cjs");
let __cache_core_index_ts = require("../cache/core/index.cjs");
let __pg_core_async_session_ts = require("../pg-core/async/session.cjs");

//#region src/neon-http/session.ts
var NeonHttpSession = class extends __pg_core_async_session_ts.PgAsyncSession {
	static [__entity_ts.entityKind] = "NeonHttpSession";
	logger;
	cache;
	/** @internal */
	client;
	constructor(client, dialect, relations, options = {}) {
		super(dialect);
		this.relations = relations;
		this.options = options;
		this.client = client;
		this.logger = options.logger ?? new __logger_ts.NoopLogger();
		this.cache = options.cache ?? new __cache_core_index_ts.NoopCache();
	}
	prepareQuery(query, mode, _name, mapper, queryMetadata, cacheConfig) {
		const executor = (params) => {
			const q = this.client.query ?? this.client;
			if (mode === "raw") return (async () => q(query.sql, params, {
				arrayMode: false,
				fullResults: true,
				authToken: this.options.authToken
			}))();
			return q(query.sql, params, {
				arrayMode: mode === "arrays",
				fullResults: true,
				authToken: this.options.authToken
			}).then((it) => it.rows);
		};
		return new __pg_core_async_session_ts.PgAsyncPreparedQuery(executor, query, mapper, mode, this.logger, this.cache, queryMetadata, cacheConfig);
	}
	async batch(queries) {
		const preparedQueries = [];
		const builtQueries = [];
		const q = this.client.query ?? this.client;
		for (const query of queries) {
			const preparedQuery = query._prepare();
			const builtQuery = preparedQuery.getQuery();
			preparedQueries.push(preparedQuery);
			builtQueries.push(q(builtQuery.sql, builtQuery.params, {
				fullResults: true,
				arrayMode: preparedQuery.mode === "arrays"
			}));
		}
		return (await this.client.transaction(builtQueries, {
			authToken: this.options.authToken,
			fullResults: true,
			arrayMode: true
		})).map((result, i) => preparedQueries[i].mapper ? preparedQueries[i].mapper(result.rows) : result);
	}
	async transaction(_transaction, _config = {}) {
		throw new Error("No transactions support in neon-http driver");
	}
};

//#endregion
exports.NeonHttpSession = NeonHttpSession;
//# sourceMappingURL=session.cjs.map