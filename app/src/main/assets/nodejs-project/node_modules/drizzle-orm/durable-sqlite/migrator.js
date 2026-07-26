import { formatToMillis, getMigrationsToRun } from "../migrator.utils.js";
import { sql } from "../sql/index.js";
import { upgradeSyncIfNeeded } from "../up-migrations/sqlite.js";

//#region src/durable-sqlite/migrator.ts
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
	const migrations = readMigrationFiles(config);
	return db.transaction((tx) => {
		try {
			const migrationsTable = "__drizzle_migrations";
			const { newDb } = upgradeSyncIfNeeded(migrationsTable, db.session, migrations);
			if (newDb) {
				const migrationTableCreate = sql`
				CREATE TABLE IF NOT EXISTS ${sql.identifier(migrationsTable)} (
					id INTEGER PRIMARY KEY,
					hash text NOT NULL,
					created_at numeric,
					name text,
					applied_at TEXT
				)
			`;
				db.run(migrationTableCreate);
			}
			const dbMigrations = db.values(sql`SELECT id, hash, created_at, name FROM ${sql.identifier(migrationsTable)}`).map(([id, hash, created_at, name]) => ({
				id,
				hash,
				created_at,
				name
			}));
			if (config.init) {
				if (dbMigrations.length) return { exitCode: "databaseMigrations" };
				if (migrations.length > 1) return { exitCode: "localMigrations" };
				const [migration] = migrations;
				if (!migration) return;
				db.run(sql`insert into ${sql.identifier(migrationsTable)} ("hash", "created_at", "name", "applied_at") values(${migration.hash}, ${migration.folderMillis}, ${migration.name}, ${(/* @__PURE__ */ new Date()).toISOString()})`);
				return;
			}
			const migrationsToRun = getMigrationsToRun({
				localMigrations: migrations,
				dbMigrations
			});
			for (const migration of migrationsToRun) {
				for (const stmt of migration.sql) db.run(sql.raw(stmt));
				db.run(sql`INSERT INTO ${sql.identifier(migrationsTable)} ("hash", "created_at", "name", "applied_at") VALUES(${migration.hash}, ${migration.folderMillis}, ${migration.name}, ${(/* @__PURE__ */ new Date()).toISOString()})`);
			}
			return;
		} catch (error) {
			tx.rollback();
			throw error;
		}
	});
}

//#endregion
export { migrate };
//# sourceMappingURL=migrator.js.map