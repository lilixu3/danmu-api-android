Object.defineProperty(exports, Symbol.toStringTag, { value: 'Module' });
const require_runtime = require('../_virtual/_rolldown/runtime.cjs');
const require_node_mssql_pool = require('./pool.cjs');
let __entity_ts = require("../entity.cjs");
let __utils_ts = require("../utils.cjs");
let __sql_sql_ts = require("../sql/sql.cjs");
let __logger_ts = require("../logger.cjs");
let node_events = require("node:events");
let mssql = require("mssql");
mssql = require_runtime.__toESM(mssql);
let __mssql_core_session_ts = require("../mssql-core/session.cjs");

//#region src/node-mssql/session.ts
var NodeMsSqlPreparedQuery = class extends __mssql_core_session_ts.PreparedQuery {
	static [__entity_ts.entityKind] = "NodeMsSqlPreparedQuery";
	jitMapper;
	rawQuery;
	constructor(client, queryString, params, logger, fields, useJitMappers, customResultMapper) {
		super();
		this.client = client;
		this.params = params;
		this.logger = logger;
		this.fields = fields;
		this.useJitMappers = useJitMappers;
		this.customResultMapper = customResultMapper;
		this.rawQuery = {
			sql: queryString,
			parameters: params
		};
	}
	async execute(placeholderValues = {}) {
		const params = (0, __sql_sql_ts.fillPlaceholders)(this.params, placeholderValues);
		this.logger.logQuery(this.rawQuery.sql, params);
		const { fields, client, rawQuery, joinsNotNullableMap, customResultMapper } = this;
		let queryClient = client;
		if ((0, __entity_ts.is)(client, require_node_mssql_pool.AutoPool)) queryClient = await client.$instance();
		const request = queryClient.request();
		for (const [index, param] of params.entries()) request.input(`par${index}`, param);
		if (!fields && !customResultMapper) return request.query(rawQuery.sql);
		request.arrayRowMode = true;
		const rows = await request.query(rawQuery.sql);
		if (customResultMapper) return customResultMapper(rows.recordset);
		return this.useJitMappers ? (this.jitMapper = this.jitMapper ?? (0, __utils_ts.makeJitQueryMapper)(fields, joinsNotNullableMap))(rows.recordset) : rows.recordset.map((row) => (0, __utils_ts.mapResultRow)(fields, row, joinsNotNullableMap));
	}
	async *iterator(placeholderValues = {}) {
		const params = (0, __sql_sql_ts.fillPlaceholders)(this.params, placeholderValues);
		const { fields, rawQuery, joinsNotNullableMap, client, customResultMapper } = this;
		let queryClient = client;
		if ((0, __entity_ts.is)(client, require_node_mssql_pool.AutoPool)) queryClient = await client.$instance();
		const request = queryClient.request();
		request.stream = true;
		const hasRowsMapper = Boolean(fields || customResultMapper);
		if (hasRowsMapper) request.arrayRowMode = true;
		for (const [index, param] of params.entries()) request.input(`par${index}`, param);
		const stream = request.toReadableStream();
		request.query(rawQuery.sql);
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
				else if (hasRowsMapper) if (customResultMapper) {
					const mappedRow = customResultMapper([row]);
					yield Array.isArray(mappedRow) ? mappedRow[0] : mappedRow;
				} else yield this.useJitMappers ? (this.jitMapper = this.jitMapper ?? (0, __utils_ts.makeJitQueryMapper)(fields, joinsNotNullableMap))([row])[0] : (0, __utils_ts.mapResultRow)(fields, row, joinsNotNullableMap);
				else yield row;
			}
		} finally {
			stream.off("data", dataListener);
			request.cancel();
		}
	}
};
var NodeMsSqlSession = class NodeMsSqlSession extends __mssql_core_session_ts.MsSqlSession {
	static [__entity_ts.entityKind] = "NodeMsSqlSession";
	logger;
	constructor(client, dialect, schema, options) {
		super(dialect);
		this.client = client;
		this.schema = schema;
		this.options = options;
		this.logger = options.logger ?? new __logger_ts.NoopLogger();
	}
	prepareQuery(query, fields, customResultMapper) {
		return new NodeMsSqlPreparedQuery(this.client, query.sql, query.params, this.logger, fields, this.options.useJitMappers, customResultMapper);
	}
	/**
	* @internal
	* What is its purpose?
	*/
	async query(query, params) {
		this.logger.logQuery(query, params);
		let queryClient = this.client;
		if ((0, __entity_ts.is)(this.client, require_node_mssql_pool.AutoPool)) queryClient = await this.client.$instance();
		const request = queryClient.request();
		request.arrayRowMode = true;
		for (const [index, param] of params.entries()) request.input(`par${index}`, param);
		return request.query(query);
	}
	async all(query) {
		const querySql = this.dialect.sqlToQuery(query);
		this.logger.logQuery(querySql.sql, querySql.params);
		return await this.query(querySql.sql, querySql.params).then((result) => result.recordset);
	}
	async transaction(transaction, config) {
		let queryClient = this.client;
		if ((0, __entity_ts.is)(this.client, require_node_mssql_pool.AutoPool)) queryClient = await this.client.$instance();
		const mssqlTransaction = queryClient.transaction();
		const session = new NodeMsSqlSession(mssqlTransaction, this.dialect, this.schema, this.options);
		const tx = new NodeMsSqlTransaction(this.dialect, session, this.schema, 0);
		await mssqlTransaction.begin(config?.isolationLevel ? isolationLevelMap[config.isolationLevel] : void 0);
		try {
			const result = await transaction(tx);
			await mssqlTransaction.commit();
			return result;
		} catch (err) {
			await mssqlTransaction.rollback();
			throw err;
		}
	}
};
var NodeMsSqlTransaction = class NodeMsSqlTransaction extends __mssql_core_session_ts.MsSqlTransaction {
	static [__entity_ts.entityKind] = "NodeMsSqlTransaction";
	async transaction(transaction) {
		const savepointName = `sp${this.nestedIndex + 1}`;
		const tx = new NodeMsSqlTransaction(this.dialect, this.session, this.schema, this.nestedIndex + 1);
		await tx.execute(__sql_sql_ts.sql.raw(`save transaction ${savepointName}`));
		try {
			return await transaction(tx);
		} catch (err) {
			await tx.execute(__sql_sql_ts.sql.raw(`rollback transaction ${savepointName}`));
			throw err;
		}
	}
};
const isolationLevelMap = {
	"read uncommitted": mssql.default.ISOLATION_LEVEL.READ_UNCOMMITTED,
	"read committed": mssql.default.ISOLATION_LEVEL.READ_COMMITTED,
	"repeatable read": mssql.default.ISOLATION_LEVEL.REPEATABLE_READ,
	serializable: mssql.default.ISOLATION_LEVEL.SERIALIZABLE,
	snapshot: mssql.default.ISOLATION_LEVEL.SNAPSHOT
};

//#endregion
exports.NodeMsSqlPreparedQuery = NodeMsSqlPreparedQuery;
exports.NodeMsSqlSession = NodeMsSqlSession;
exports.NodeMsSqlTransaction = NodeMsSqlTransaction;
//# sourceMappingURL=session.cjs.map