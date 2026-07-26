Object.defineProperty(exports, Symbol.toStringTag, { value: 'Module' });
const require_runtime = require('../_virtual/_rolldown/runtime.cjs');
let __migrator_ts = require("../migrator.cjs");

//#region src/cockroach/migrator.ts
async function migrate(db, config) {
	const migrations = (0, __migrator_ts.readMigrationFiles)(config);
	return await db.dialect.migrate(migrations, db.session, config);
}

//#endregion
exports.migrate = migrate;
//# sourceMappingURL=migrator.cjs.map