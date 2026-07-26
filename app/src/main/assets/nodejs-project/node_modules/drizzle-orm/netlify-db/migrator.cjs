Object.defineProperty(exports, Symbol.toStringTag, { value: 'Module' });
const require_runtime = require('../_virtual/_rolldown/runtime.cjs');
const require_node_postgres_driver = require('../node-postgres/driver.cjs');
let __entity_ts = require("../entity.cjs");
let __migrator_ts = require("../migrator.cjs");
let __pg_core_async_session_ts = require("../pg-core/async/session.cjs");
let __node_postgres_migrator_ts = require("../node-postgres/migrator.cjs");

//#region src/netlify-db/migrator.ts
async function migrate(db, config) {
	if ((0, __entity_ts.is)(db, require_node_postgres_driver.NodePgDatabase)) return (0, __node_postgres_migrator_ts.migrate)(db, config);
	return (0, __pg_core_async_session_ts.migrate)((0, __migrator_ts.readMigrationFiles)(config), db, config);
}

//#endregion
exports.migrate = migrate;
//# sourceMappingURL=migrator.cjs.map