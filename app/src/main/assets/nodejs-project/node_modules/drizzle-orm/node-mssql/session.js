import { AutoPool } from "./pool.js";
import { entityKind, is } from "../entity.js";
import { makeJitQueryMapper, mapResultRow } from "../utils.js";
import { fillPlaceholders, sql } from "../sql/sql.js";
import { NoopLogger } from "../logger.js";
import { once } from "node:events";
import mssql from "mssql";
import { MsSqlSession, MsSqlTransaction, PreparedQuery } from "../mssql-core/session.js";

//#region src/node-mssql/session.ts
var NodeMsSqlPreparedQuery = class extends PreparedQuery {
	static [entityKind] = "NodeMsSqlPreparedQuery";
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
		const params = fillPlaceholders(this.params, placeholderValues);
		this.logger.logQuery(this.rawQuery.sql, params);
		const { fields, client, rawQuery, joinsNotNullableMap, customResultMapper } = this;
		let queryClient = client;
		if (is(client, AutoPool)) queryClient = await client.$instance();
		const request = queryClient.request();
		for (const [index, param] of params.entries()) request.input(`par${index}`, param);
		if (!fields && !customResultMapper) return request.query(rawQuery.sql);
		request.arrayRowMode = true;
		const rows = await request.query(rawQuery.sql);
		if (customResultMapper) return customResultMapper(rows.recordset);
		return this.useJitMappers ? (this.jitMapper = this.jitMapper ?? makeJitQueryMapper(fields, joinsNotNullableMap))(rows.recordset) : rows.recordset.map((row) => mapResultRow(fields, row, joinsNotNullableMap));
	}
	async *iterator(placeholderValues = {}) {
		const params = fillPlaceholders(this.params, placeholderValues);
		const { fields, rawQuery, joinsNotNullableMap, client, customResultMapper } = this;
		let queryClient = client;
		if (is(client, AutoPool)) queryClient = await client.$instance();
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
			const onEnd = once(stream, "end");
			const onError = once(stream, "error");
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
				} else yield this.useJitMappers ? (this.jitMapper = this.jitMapper ?? makeJitQueryMapper(fields, joinsNotNullableMap))([row])[0] : mapResultRow(fields, row, joinsNotNullableMap);
				else yield row;
			}
		} finally {
			stream.off("data", dataListener);
			request.cancel();
		}
	}
};
var NodeMsSqlSession = class NodeMsSqlSession extends MsSqlSession {
	static [entityKind] = "NodeMsSqlSession";
	logger;
	constructor(client, dialect, schema, options) {
		super(dialect);
		this.client = client;
		this.schema = schema;
		this.options = options;
		this.logger = options.logger ?? new NoopLogger();
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
		if (is(this.client, AutoPool)) queryClient = await this.client.$instance();
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
		if (is(this.client, AutoPool)) queryClient = await this.client.$instance();
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
var NodeMsSqlTransaction = class NodeMsSqlTransaction extends MsSqlTransaction {
	static [entityKind] = "NodeMsSqlTransaction";
	async transaction(transaction) {
		const savepointName = `sp${this.nestedIndex + 1}`;
		const tx = new NodeMsSqlTransaction(this.dialect, this.session, this.schema, this.nestedIndex + 1);
		await tx.execute(sql.raw(`save transaction ${savepointName}`));
		try {
			return await transaction(tx);
		} catch (err) {
			await tx.execute(sql.raw(`rollback transaction ${savepointName}`));
			throw err;
		}
	}
};
const isolationLevelMap = {
	"read uncommitted": mssql.ISOLATION_LEVEL.READ_UNCOMMITTED,
	"read committed": mssql.ISOLATION_LEVEL.READ_COMMITTED,
	"repeatable read": mssql.ISOLATION_LEVEL.REPEATABLE_READ,
	serializable: mssql.ISOLATION_LEVEL.SERIALIZABLE,
	snapshot: mssql.ISOLATION_LEVEL.SNAPSHOT
};

//#endregion
export { NodeMsSqlPreparedQuery, NodeMsSqlSession, NodeMsSqlTransaction };
//# sourceMappingURL=session.js.map