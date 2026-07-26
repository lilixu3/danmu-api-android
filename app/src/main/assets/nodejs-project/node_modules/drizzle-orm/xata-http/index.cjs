Object.defineProperty(exports, Symbol.toStringTag, { value: 'Module' });
const require_xata_http_codecs = require('./codecs.cjs');
const require_xata_http_session = require('./session.cjs');
const require_xata_http_driver = require('./driver.cjs');

exports.XataHttpDatabase = require_xata_http_driver.XataHttpDatabase;
exports.XataHttpSession = require_xata_http_session.XataHttpSession;
exports.drizzle = require_xata_http_driver.drizzle;
exports.xataHttpCodecs = require_xata_http_codecs.xataHttpCodecs;