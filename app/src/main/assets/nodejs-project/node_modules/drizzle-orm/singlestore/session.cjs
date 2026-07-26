Object.defineProperty(exports, Symbol.toStringTag, { value: 'Module' });
const require_runtime = require('../_virtual/_rolldown/runtime.cjs');
let __entity_ts = require("../entity.cjs");
let __utils_ts = require("../utils.cjs");
let __column_ts = require("../column.cjs");
let __sql_sql_ts = require("../sql/sql.cjs");
let __logger_ts = require("../logger.cjs");
let __cache_core_index_ts = require("../cache/core/index.cjs");
let node_events = require("node:events");
let __relations_ts = require("../relations.cjs");
let __singlestore_core_session_ts = require("../singlestore-core/session.cjs");

//#region src/singlestore/session.ts
var SingleStoreDriverPreparedQuery = class extends __singlestore_core_session_ts.SingleStorePreparedQuery {
	static [__entity_ts.entityKind] = "SingleStoreDriverPreparedQuery";
	rawQuery;
	query;
	jitMapper;
	constructor(client, queryString, params, logger, cache, queryMetadata, cacheConfig, fields, useJitMappers, customResultMapper, generatedIds, returningIds, isRqbV2Query, rqbConfig) {
		super(cache, queryMetadata, cacheConfig);
		this.client = client;
		this.params = params;
		this.logger = logger;
		this.fields = fields;
		this.useJitMappers = useJitMappers;
		this.customResultMapper = customResultMapper;
		this.generatedIds = generatedIds;
		this.returningIds = returningIds;
		this.isRqbV2Query = isRqbV2Query;
		this.rqbConfig = rqbConfig;
		this.rawQuery = {
			sql: queryString,
			typeCast: function(field, next) {
				if (field.type === "TIMESTAMP" || field.type === "DATETIME" || field.type === "DATE") return field.string();
				return next();
			}
		};
		this.query = {
			sql: queryString,
			rowsAsArray: true,
			typeCast: function(field, next) {
				if (field.type === "TIMESTAMP" || field.type === "DATETIME" || field.type === "DATE") return field.string();
				return next();
			}
		};
	}
	async execute(placeholderValues = {}) {
		if (this.isRqbV2Query) return this.executeRqbV2(placeholderValues);
		const params = (0, __sql_sql_ts.fillPlaceholders)(this.params, placeholderValues);
		this.logger.logQuery(this.rawQuery.sql, params);
		const { fields, client, rawQuery, query, joinsNotNullableMap, customResultMapper, returningIds, generatedIds } = this;
		if (!fields && !customResultMapper) {
			const res = await this.queryWithCache(rawQuery.sql, params, async () => {
				return await client.query(rawQuery, params);
			});
			const insertId = res[0].insertId;
			const affectedRows = res[0].affectedRows;
			if (returningIds) {
				const returningResponse = [];
				let j = 0;
				for (let i = insertId; i < insertId + affectedRows; i++) {
					for (const column of returningIds) {
						const key = returningIds[0].path[0];
						if ((0, __entity_ts.is)(column.field, __column_ts.Column)) {
							if (column.field.primary && column.field.autoIncrement) returningResponse.push({ [key]: i });
							if (column.field.defaultFn && generatedIds) returningResponse.push({ [key]: generatedIds[j][key] });
						}
					}
					j++;
				}
				return returningResponse;
			}
			return res;
		}
		const rows = (await this.queryWithCache(query.sql, params, async () => {
			return await client.query(query, params);
		}))[0];
		if (customResultMapper) return customResultMapper(rows);
		return this.useJitMappers ? (this.jitMapper = this.jitMapper ?? (0, __utils_ts.makeJitQueryMapper)(fields, joinsNotNullableMap))(rows) : rows.map((row) => (0, __utils_ts.mapResultRow)(fields, row, joinsNotNullableMap));
	}
	async executeRqbV2(placeholderValues = {}) {
		const params = (0, __sql_sql_ts.fillPlaceholders)(this.params, placeholderValues);
		this.logger.logQuery(this.rawQuery.sql, params);
		const { client, rawQuery, customResultMapper } = this;
		const rows = (await client.query(rawQuery, params))[0];
		return customResultMapper(rows);
	}
	async *iterator(placeholderValues = {}) {
		const params = (0, __sql_sql_ts.fillPlaceholders)(this.params, placeholderValues);
		const conn = (isPool(this.client) ? await this.client.getConnection() : this.client).connection;
		const { fields, query, rawQuery, joinsNotNullableMap, client, customResultMapper } = this;
		const hasRowsMapper = Boolean(fields || customResultMapper);
		const stream = (hasRowsMapper ? conn.query(query, params) : conn.query(rawQuery, params)).stream();
		function dataListener() {
			stream.pause();
		}
		stream.on("data", dataListener);
		try {
			const onEnd = (0, node_events.once)(stream, "end");
			const onError = (0, node_events.once)(stream, "error");
			while (true) {
				stream.resume();
				const row = await Promise.race([
					onEnd,
					onError,
					new Promise((resolve) => stream.once("data", resolve))
				]);
				if (row === void 0 || Array.isArray(row) && row.length === 0) break;
				else if (row instanceof Error) throw row;
				else if (this.isRqbV2Query) if (this.useJitMappers) yield (this.jitMapper = this.jitMapper ?? (0, __relations_ts.makeJitRqbMapper)(this.rqbConfig))([row]);
				else {
					const mapped = customResultMapper([row]);
					if (this.rqbConfig.isFirst) yield mapped;
					else yield mapped[0];
				}
				else if (hasRowsMapper) if (customResultMapper) {
					const mappedRow = customResultMapper([row]);
					yield Array.isArray(mappedRow) ? mappedRow[0] : mappedRow;
				} else yield this.useJitMappers ? (this.jitMapper = this.jitMapper ?? (0, __utils_ts.makeJitQueryMapper)(fields, joinsNotNullableMap))([row])[0] : (0, __utils_ts.mapResultRow)(fields, row, joinsNotNullableMap);
				else yield row;
			}
		} finally {
			stream.off("data", dataListener);
			if (isPool(client)) conn.end();
		}
	}
};
var SingleStoreDriverSession = class SingleStoreDriverSession extends __singlestore_core_session_ts.SingleStoreSession {
	static [__entity_ts.entityKind] = "SingleStoreDriverSession";
	logger;
	cache;
	constructor(client, dialect, relations, schema, options) {
		super(dialect);
		this.client = client;
		this.relations = relations;
		this.schema = schema;
		this.options = options;
		this.logger = options.logger ?? new __logger_ts.NoopLogger();
		this.cache = options.cache ?? new __cache_core_index_ts.NoopCache();
	}
	prepareQuery(query, fields, customResultMapper, generatedIds, returningIds, queryMetadata, cacheConfig) {
		return new SingleStoreDriverPreparedQuery(this.client, query.sql, query.params, this.logger, this.cache, queryMetadata, cacheConfig, fields, this.options.useJitMappers, customResultMapper, generatedIds, returningIds);
	}
	prepareRelationalQuery(query, fields, customResultMapper, config, generatedIds, returningIds) {
		return new SingleStoreDriverPreparedQuery(this.client, query.sql, query.params, this.logger, this.cache, void 0, void 0, fields, this.options.useJitMappers, customResultMapper, generatedIds, returningIds, true, config);
	}
	/**
	* @internal
	* What is its purpose?
	*/
	async query(query, params) {
		this.logger.logQuery(query, params);
		return await this.client.query({
			sql: query,
			values: params,
			rowsAsArray: true,
			typeCast: function(field, next) {
				if (field.type === "TIMESTAMP" || field.type === "DATETIME" || field.type === "DATE") return field.string();
				return next();
			}
		});
	}
	all(query) {
		const querySql = this.dialect.sqlToQuery(query);
		this.logger.logQuery(querySql.sql, querySql.params);
		return this.client.query(querySql.sql, querySql.params).then((result) => result[0]);
	}
	async transaction(transaction, config) {
		const session = isPool(this.client) ? new SingleStoreDriverSession(await this.client.getConnection(), this.dialect, this.relations, this.schema, this.options) : this;
		const tx = new SingleStoreDriverTransaction(this.dialect, session, this.relations, this.schema, 0);
		if (config) {
			const setTransactionConfigSql = this.getSetTransactionSQL(config);
			if (setTransactionConfigSql) await tx.execute(setTransactionConfigSql);
			const startTransactionSql = this.getStartTransactionSQL(config);
			await (startTransactionSql ? tx.execute(startTransactionSql) : tx.execute(__sql_sql_ts.sql`begin`));
		} else await tx.execute(__sql_sql_ts.sql`begin`);
		try {
			const result = await transaction(tx);
			await tx.execute(__sql_sql_ts.sql`commit`);
			return result;
		} catch (err) {
			await tx.execute(__sql_sql_ts.sql`rollback`);
			throw err;
		} finally {
			if (isPool(this.client)) session.client.release();
		}
	}
};
var SingleStoreDriverTransaction = class SingleStoreDriverTransaction extends __singlestore_core_session_ts.SingleStoreTransaction {
	static [__entity_ts.entityKind] = "SingleStoreDriverTransaction";
	async transaction(transaction) {
		const savepointName = `sp${this.nestedIndex + 1}`;
		const tx = new SingleStoreDriverTransaction(this.dialect, this.session, this.relations, this.schema, this.nestedIndex + 1);
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
function isPool(client) {
	return "getConnection" in client;
}

//#endregion
exports.SingleStoreDriverPreparedQuery = SingleStoreDriverPreparedQuery;
exports.SingleStoreDriverSession = SingleStoreDriverSession;
exports.SingleStoreDriverTransaction = SingleStoreDriverTransaction;
//# sourceMappingURL=session.cjs.map