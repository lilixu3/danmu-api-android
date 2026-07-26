import { formatToMillis } from "../migrator.utils.js";
import { migrate as migrate$1 } from "../sqlite-core/effect/session.js";

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
			const migrationDate = formatToMillis(key.slice(0, 14));
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
	return migrate$1(readMigrationFiles(config), db.session, config);
}

//#endregion
export { migrate };
//# sourceMappingURL=migrator.js.map