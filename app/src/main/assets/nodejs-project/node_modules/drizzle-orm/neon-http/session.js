import { entityKind } from "../entity.js";
import { NoopLogger } from "../logger.js";
import { NoopCache } from "../cache/core/index.js";
import { PgAsyncPreparedQuery, PgAsyncSession } from "../pg-core/async/session.js";

//#region src/neon-http/session.ts
var NeonHttpSession = class extends PgAsyncSession {
	static [entityKind] = "NeonHttpSession";
	logger;
	cache;
	/** @internal */
	client;
	constructor(client, dialect, relations, options = {}) {
		super(dialect);
		this.relations = relations;
		this.options = options;
		this.client = client;
		this.logger = options.logger ?? new NoopLogger();
		this.cache = options.cache ?? new NoopCache();
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
		return new PgAsyncPreparedQuery(executor, query, mapper, mode, this.logger, this.cache, queryMetadata, cacheConfig);
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
export { NeonHttpSession };
//# sourceMappingURL=session.js.map