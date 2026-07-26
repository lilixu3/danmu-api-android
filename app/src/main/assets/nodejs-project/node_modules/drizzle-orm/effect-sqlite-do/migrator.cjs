Object.defineProperty(exports, Symbol.toStringTag, { value: 'Module' });
const require_runtime = require('../_virtual/_rolldown/runtime.cjs');
let __migrator_utils_ts = require("../migrator.utils.cjs");
let __sqlite_core_effect_session_ts = require("../sqlite-core/effect/session.cjs");

//#region src/effect-sqlite-do/migrator.ts
function readMigrationFiles({ migrations }) {
	const migrationQueries = [];
	const sortedMigrations = Object.keys(migrations).sort();
	for (const key of sortedMigrations) {
		const query = migrations[key];
		if (!query) throw new Error(`Missing migration: ${key}`);
		try {
			const result = query.split("--> statement-breakpoint").map((it) => {
				return it;
			});
			const migrationDate = (0, __migrator_utils_ts.formatToMillis)(key.slice(0, 14));
			migrationQueries.push({
				sql: result,
				bps: true,
				folderMillis: migrationDate,
				hash: "",
				name: key
			});
		} catch {
			throw new Error(`Failed to parse migration: ${key}`);
		}
	}
	return migrationQueries;
}
function migrate(db, config) {
	return (0, __sqlite_core_effect_session_ts.migrate)(readMigrationFiles(config), db.session, config);
}

//#endregion
exports.migrate = migrate;
//# sourceMappingURL=migrator.cjs.map