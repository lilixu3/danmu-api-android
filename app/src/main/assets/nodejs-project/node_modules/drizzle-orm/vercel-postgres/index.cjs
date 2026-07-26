Object.defineProperty(exports, Symbol.toStringTag, { value: 'Module' });
const require_vercel_postgres_codecs = require('./codecs.cjs');
const require_vercel_postgres_session = require('./session.cjs');
const require_vercel_postgres_driver = require('./driver.cjs');

exports.VercelPgDatabase = require_vercel_postgres_driver.VercelPgDatabase;
exports.VercelPgSession = require_vercel_postgres_session.VercelPgSession;
exports.VercelPgTransaction = require_vercel_postgres_session.VercelPgTransaction;
Object.defineProperty(exports, 'drizzle', {
  enumerable: true,
  get: function () {
    return require_vercel_postgres_driver.drizzle;
  }
});
exports.vercelPgCodecs = require_vercel_postgres_codecs.vercelPgCodecs;