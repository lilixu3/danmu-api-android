Object.defineProperty(exports, Symbol.toStringTag, { value: 'Module' });
const require_runtime = require('../_virtual/_rolldown/runtime.cjs');
let __migrator_ts = require("../migrator.cjs");
let __sqlite_core_effect_session_ts = require("../sqlite-core/effect/session.cjs");

//#region src/effect-sqlite-bun/migrator.ts
function migrate(db, config) {
	return (0, __sqlite_core_effect_session_ts.migrate)((0, __migrator_ts.readMigrationFiles)(config), db.session, config);
}

//#endregion
exports.migrate = migrate;
//# sourceMappingURL=migrator.cjs.map