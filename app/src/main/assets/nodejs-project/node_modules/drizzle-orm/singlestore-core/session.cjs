Object.defineProperty(exports, Symbol.toStringTag, { value: 'Module' });
const require_runtime = require('../_virtual/_rolldown/runtime.cjs');
const require_singlestore_core_db = require('./db.cjs');
let __entity_ts = require("../entity.cjs");
let __sql_sql_ts = require("../sql/sql.cjs");
let __errors_ts = require("../errors.cjs");
let __cache_core_cache_ts = require("../cache/core/cache.cjs");

//#region src/singlestore-core/session.ts
var SingleStorePreparedQuery = class {
	static [__entity_ts.entityKind] = "SingleStorePreparedQuery";
	constructor(cache, queryMetadata, cacheConfig) {
		this.cache = cache;
		this.queryMetadata = queryMetadata;
		this.cacheConfig = cacheConfig;
		if (cache && cache.strategy() === "all" && cacheConfig === void 0) this.cacheConfig = {
			enabled: true,
			autoInvalidate: true
		};
		if (!this.cacheConfig?.enabled) this.cacheConfig = void 0;
	}
	/** @internal */
	async queryWithCache(queryString, params, query) {
		if (this.cache === void 0 || (0, __entity_ts.is)(this.cache, __cache_core_cache_ts.NoopCache) || this.queryMetadata === void 0) try {
			return await query();
		} catch (e) {
			throw new __errors_ts.DrizzleQueryError(queryString, params, e);
		}
		if (this.cacheConfig && !this.cacheConfig.enabled) try {
			return await query();
		} catch (e) {
			throw new __errors_ts.DrizzleQueryError(queryString, params, e);
		}
		if ((this.queryMetadata.type === "insert" || this.queryMetadata.type === "update" || this.queryMetadata.type === "delete") && this.queryMetadata.tables.length > 0) try {
			const [res] = await Promise.all([query(), this.cache.onMutate({ tables: this.queryMetadata.tables })]);
			return res;
		} catch (e) {
			throw new __errors_ts.DrizzleQueryError(queryString, params, e);
		}
		if (!this.cacheConfig) try {
			return await query();
		} catch (e) {
			throw new __errors_ts.DrizzleQueryError(queryString, params, e);
		}
		if (this.queryMetadata.type === "select") {
			const fromCache = await this.cache.get(this.cacheConfig.tag ?? await (0, __cache_core_cache_ts.hashQuery)(queryString, params), this.queryMetadata.tables, this.cacheConfig.tag !== void 0, this.cacheConfig.autoInvalidate);
			if (fromCache === void 0) {
				let result;
				try {
					result = await query();
				} catch (e) {
					throw new __errors_ts.DrizzleQueryError(queryString, params, e);
				}
				await this.cache.put(this.cacheConfig.tag ?? await (0, __cache_core_cache_ts.hashQuery)(queryString, params), result, this.cacheConfig.autoInvalidate ? this.queryMetadata.tables : [], this.cacheConfig.tag !== void 0, this.cacheConfig.config);
				return result;
			}
			return fromCache;
		}
		try {
			return await query();
		} catch (e) {
			throw new __errors_ts.DrizzleQueryError(queryString, params, e);
		}
	}
	/** @internal */
	joinsNotNullableMap;
};
var SingleStoreSession = class {
	static [__entity_ts.entityKind] = "SingleStoreSession";
	constructor(dialect) {
		this.dialect = dialect;
	}
	execute(query) {
		return this.prepareQuery(this.dialect.sqlToQuery(query), void 0).execute();
	}
	async count(sql) {
		const res = await this.execute(sql);
		return Number(res[0][0]["count"]);
	}
	getSetTransactionSQL(config) {
		const parts = [];
		if (config.isolationLevel) parts.push(`isolation level ${config.isolationLevel}`);
		return parts.length ? __sql_sql_ts.sql`set transaction ${__sql_sql_ts.sql.raw(parts.join(" "))}` : void 0;
	}
	getStartTransactionSQL(config) {
		const parts = [];
		if (config.withConsistentSnapshot) parts.push("with consistent snapshot");
		if (config.accessMode) parts.push(config.accessMode);
		return parts.length ? __sql_sql_ts.sql`start transaction ${__sql_sql_ts.sql.raw(parts.join(" "))}` : void 0;
	}
};
var SingleStoreTransaction = class extends require_singlestore_core_db.SingleStoreDatabase {
	static [__entity_ts.entityKind] = "SingleStoreTransaction";
	constructor(dialect, session, relations, schema, nestedIndex) {
		super(dialect, session, relations, schema);
		this.relations = relations;
		this.schema = schema;
		this.nestedIndex = nestedIndex;
	}
	rollback() {
		throw new __errors_ts.TransactionRollbackError();
	}
};

//#endregion
exports.SingleStorePreparedQuery = SingleStorePreparedQuery;
exports.SingleStoreSession = SingleStoreSession;
exports.SingleStoreTransaction = SingleStoreTransaction;
//# sourceMappingURL=session.cjs.map