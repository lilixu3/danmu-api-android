import { SingleStoreDatabase } from "./db.js";
import { entityKind, is } from "../entity.js";
import { sql } from "../sql/sql.js";
import { DrizzleQueryError, TransactionRollbackError } from "../errors.js";
import { NoopCache, hashQuery } from "../cache/core/cache.js";

//#region src/singlestore-core/session.ts
var SingleStorePreparedQuery = class {
	static [entityKind] = "SingleStorePreparedQuery";
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
		if (this.cache === void 0 || is(this.cache, NoopCache) || this.queryMetadata === void 0) try {
			return await query();
		} catch (e) {
			throw new DrizzleQueryError(queryString, params, e);
		}
		if (this.cacheConfig && !this.cacheConfig.enabled) try {
			return await query();
		} catch (e) {
			throw new DrizzleQueryError(queryString, params, e);
		}
		if ((this.queryMetadata.type === "insert" || this.queryMetadata.type === "update" || this.queryMetadata.type === "delete") && this.queryMetadata.tables.length > 0) try {
			const [res] = await Promise.all([query(), this.cache.onMutate({ tables: this.queryMetadata.tables })]);
			return res;
		} catch (e) {
			throw new DrizzleQueryError(queryString, params, e);
		}
		if (!this.cacheConfig) try {
			return await query();
		} catch (e) {
			throw new DrizzleQueryError(queryString, params, e);
		}
		if (this.queryMetadata.type === "select") {
			const fromCache = await this.cache.get(this.cacheConfig.tag ?? await hashQuery(queryString, params), this.queryMetadata.tables, this.cacheConfig.tag !== void 0, this.cacheConfig.autoInvalidate);
			if (fromCache === void 0) {
				let result;
				try {
					result = await query();
				} catch (e) {
					throw new DrizzleQueryError(queryString, params, e);
				}
				await this.cache.put(this.cacheConfig.tag ?? await hashQuery(queryString, params), result, this.cacheConfig.autoInvalidate ? this.queryMetadata.tables : [], this.cacheConfig.tag !== void 0, this.cacheConfig.config);
				return result;
			}
			return fromCache;
		}
		try {
			return await query();
		} catch (e) {
			throw new DrizzleQueryError(queryString, params, e);
		}
	}
	/** @internal */
	joinsNotNullableMap;
};
var SingleStoreSession = class {
	static [entityKind] = "SingleStoreSession";
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
		return parts.length ? sql`set transaction ${sql.raw(parts.join(" "))}` : void 0;
	}
	getStartTransactionSQL(config) {
		const parts = [];
		if (config.withConsistentSnapshot) parts.push("with consistent snapshot");
		if (config.accessMode) parts.push(config.accessMode);
		return parts.length ? sql`start transaction ${sql.raw(parts.join(" "))}` : void 0;
	}
};
var SingleStoreTransaction = class extends SingleStoreDatabase {
	static [entityKind] = "SingleStoreTransaction";
	constructor(dialect, session, relations, schema, nestedIndex) {
		super(dialect, session, relations, schema);
		this.relations = relations;
		this.schema = schema;
		this.nestedIndex = nestedIndex;
	}
	rollback() {
		throw new TransactionRollbackError();
	}
};

//#endregion
export { SingleStorePreparedQuery, SingleStoreSession, SingleStoreTransaction };
//# sourceMappingURL=session.js.map