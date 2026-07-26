Object.defineProperty(exports, Symbol.toStringTag, { value: 'Module' });
const require_runtime = require('../../_virtual/_rolldown/runtime.cjs');
const require_aws_data_api_pg_codecs = require('./codecs.cjs');
const require_aws_data_api_pg_session = require('./session.cjs');
let __entity_ts = require("../../entity.cjs");
let __utils_ts = require("../../utils.cjs");
let __logger_ts = require("../../logger.cjs");
let __pg_core_async_db_ts = require("../../pg-core/async/db.cjs");
let __pg_core_dialect_ts = require("../../pg-core/dialect.cjs");
let _aws_sdk_client_rds_data = require("@aws-sdk/client-rds-data");

//#region src/aws-data-api/pg/driver.ts
var AwsDataApiPgDatabase = class extends __pg_core_async_db_ts.PgAsyncDatabase {
	static [__entity_ts.entityKind] = "AwsDataApiPgDatabase";
};
var AwsPgDialect = class extends __pg_core_dialect_ts.PgDialect {
	static [__entity_ts.entityKind] = "AwsPgDialect";
	escapeParam(num) {
		return `:${num + 1}`;
	}
};
function construct(client, config) {
	const dialect = new AwsPgDialect({
		useJitMappers: (0, __utils_ts.jitCompatCheck)(config.jit),
		codecs: config.codecs ?? require_aws_data_api_pg_codecs.awsDataApiPgCodecs
	});
	let logger;
	if (config.logger === true) logger = new __logger_ts.DefaultLogger();
	else if (config.logger !== false) logger = config.logger;
	const relations = config.relations ?? {};
	const db = new AwsDataApiPgDatabase(dialect, new require_aws_data_api_pg_session.AwsDataApiSession(client, dialect, relations, {
		...config,
		logger,
		cache: config.cache
	}, void 0), relations, true);
	db.$client = client;
	db.$cache = config.cache;
	if (db.$cache) db.$cache["invalidate"] = config.cache?.onMutate;
	return db;
}
function drizzle(...params) {
	if (params[0].client) {
		const { client, ...drizzleConfig } = params[0];
		return construct(client, drizzleConfig);
	}
	const { connection, ...drizzleConfig } = params[0];
	const { resourceArn, database, secretArn, ...rdsConfig } = connection;
	return construct(new _aws_sdk_client_rds_data.RDSDataClient(rdsConfig), {
		resourceArn,
		database,
		secretArn,
		...drizzleConfig
	});
}
(function(_drizzle) {
	function mock(config) {
		return construct({}, config);
	}
	_drizzle.mock = mock;
})(drizzle || (drizzle = {}));

//#endregion
exports.AwsDataApiPgDatabase = AwsDataApiPgDatabase;
exports.AwsPgDialect = AwsPgDialect;
Object.defineProperty(exports, 'drizzle', {
  enumerable: true,
  get: function () {
    return drizzle;
  }
});
//# sourceMappingURL=driver.cjs.map