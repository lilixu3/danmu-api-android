Object.defineProperty(exports, Symbol.toStringTag, { value: 'Module' });
const require_runtime = require('../_virtual/_rolldown/runtime.cjs');
let __migrator_ts = require("../migrator.cjs");
let __pg_core_async_session_ts = require("../pg-core/async/session.cjs");

//#region src/pglite/migrator.ts
async function migrate(db, config) {
	return await (0, __pg_core_async_session_ts.migrate)((0, __migrator_ts.readMigrationFiles)(config), db, config);
}

//#endregion
exports.migrate = migrate;
//# sourceMappingURL=migrator.cjs.map