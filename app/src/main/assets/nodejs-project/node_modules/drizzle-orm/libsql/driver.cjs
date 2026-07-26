Object.defineProperty(exports, Symbol.toStringTag, { value: 'Module' });
const require_runtime = require('../_virtual/_rolldown/runtime.cjs');
const require_libsql_driver_core = require('./driver-core.cjs');
let _libsql_client = require("@libsql/client");

//#region src/libsql/driver.ts
function drizzle(...params) {
	if (typeof params[0] === "string") return require_libsql_driver_core.construct((0, _libsql_client.createClient)({ url: params[0] }), params[1]);
	const { connection, client, ...DrizzleSQLiteConfig } = params[0];
	if (client) return require_libsql_driver_core.construct(client, DrizzleSQLiteConfig);
	return require_libsql_driver_core.construct(typeof connection === "string" ? (0, _libsql_client.createClient)({ url: connection }) : (0, _libsql_client.createClient)(connection), DrizzleSQLiteConfig);
}
(function(_drizzle) {
	function mock(config) {
		return require_libsql_driver_core.construct({}, config);
	}
	_drizzle.mock = mock;
})(drizzle || (drizzle = {}));

//#endregion
exports.LibSQLDatabase = require_libsql_driver_core.LibSQLDatabase;
Object.defineProperty(exports, 'drizzle', {
  enumerable: true,
  get: function () {
    return drizzle;
  }
});
//# sourceMappingURL=driver.cjs.map