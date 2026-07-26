Object.defineProperty(exports, Symbol.toStringTag, { value: 'Module' });
const require_aws_data_api_pg_codecs = require('./codecs.cjs');
const require_aws_data_api_pg_session = require('./session.cjs');
const require_aws_data_api_pg_driver = require('./driver.cjs');

exports.AwsDataApiPgDatabase = require_aws_data_api_pg_driver.AwsDataApiPgDatabase;
exports.AwsDataApiSession = require_aws_data_api_pg_session.AwsDataApiSession;
exports.AwsDataApiTransaction = require_aws_data_api_pg_session.AwsDataApiTransaction;
exports.AwsPgDialect = require_aws_data_api_pg_driver.AwsPgDialect;
exports.awsDataApiPgCodecs = require_aws_data_api_pg_codecs.awsDataApiPgCodecs;
Object.defineProperty(exports, 'drizzle', {
  enumerable: true,
  get: function () {
    return require_aws_data_api_pg_driver.drizzle;
  }
});