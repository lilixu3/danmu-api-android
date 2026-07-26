Object.defineProperty(exports, Symbol.toStringTag, { value: 'Module' });
const require_runtime = require('../_virtual/_rolldown/runtime.cjs');
let __migrator_ts = require("../migrator.cjs");
let __pg_core_effect_session_ts = require("../pg-core/effect/session.cjs");

//#region src/effect-postgres/migrator.ts
function migrate(db, config) {
	return (0, __pg_core_effect_session_ts.migrate)((0, __migrator_ts.readMigrationFiles)(config), db.session, config);
}

//#endregion
exports.migrate = migrate;
//# sourceMappingURL=migrator.cjs.map