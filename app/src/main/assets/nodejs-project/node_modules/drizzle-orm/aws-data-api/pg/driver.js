import { awsDataApiPgCodecs } from "./codecs.js";
import { AwsDataApiSession } from "./session.js";
import { entityKind } from "../../entity.js";
import { jitCompatCheck } from "../../utils.js";
import { DefaultLogger } from "../../logger.js";
import { PgAsyncDatabase } from "../../pg-core/async/db.js";
import { PgDialect } from "../../pg-core/dialect.js";
import { RDSDataClient } from "@aws-sdk/client-rds-data";

//#region src/aws-data-api/pg/driver.ts
var AwsDataApiPgDatabase = class extends PgAsyncDatabase {
	static [entityKind] = "AwsDataApiPgDatabase";
};
var AwsPgDialect = class extends PgDialect {
	static [entityKind] = "AwsPgDialect";
	escapeParam(num) {
		return `:${num + 1}`;
	}
};
function construct(client, config) {
	const dialect = new AwsPgDialect({
		useJitMappers: jitCompatCheck(config.jit),
		codecs: config.codecs ?? awsDataApiPgCodecs
	});
	let logger;
	if (config.logger === true) logger = new DefaultLogger();
	else if (config.logger !== false) logger = config.logger;
	const relations = config.relations ?? {};
	const db = new AwsDataApiPgDatabase(dialect, new AwsDataApiSession(client, dialect, relations, {
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
	return construct(new RDSDataClient(rdsConfig), {
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
export { AwsDataApiPgDatabase, AwsPgDialect, drizzle };
//# sourceMappingURL=driver.js.map