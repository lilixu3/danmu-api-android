Object.defineProperty(exports, Symbol.toStringTag, { value: 'Module' });
const require_runtime = require('../_virtual/_rolldown/runtime.cjs');
let __migrator_utils_ts = require("../migrator.utils.cjs");
let __sql_index_ts = require("../sql/index.cjs");
let __up_migrations_sqlite_ts = require("../up-migrations/sqlite.cjs");

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
	const migrations = readMigrationFiles(config);
	return db.transaction((tx) => {
		try {
			const migrationsTable = "__drizzle_migrations";
			const { newDb } = (0, __up_migrations_sqlite_ts.upgradeSyncIfNeeded)(migrationsTable, db.session, migrations);
			if (newDb) {
				const migrationTableCreate = __sql_index_ts.sql`
				CREATE TABLE IF NOT EXISTS ${__sql_index_ts.sql.identifier(migrationsTable)} (
					id INTEGER PRIMARY KEY,
					hash text NOT NULL,
					created_at numeric,
					name text,
					applied_at TEXT
				)
			`;
				db.run(migrationTableCreate);
			}
			const dbMigrations = db.values(__sql_index_ts.sql`SELECT id, hash, created_at, name FROM ${__sql_index_ts.sql.identifier(migrationsTable)}`).map(([id, hash, created_at, name]) => ({
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
				db.run(__sql_index_ts.sql`insert into ${__sql_index_ts.sql.identifier(migrationsTable)} ("hash", "created_at", "name", "applied_at") values(${migration.hash}, ${migration.folderMillis}, ${migration.name}, ${(/* @__PURE__ */ new Date()).toISOString()})`);
				return;
			}
			const migrationsToRun = (0, __migrator_utils_ts.getMigrationsToRun)({
				localMigrations: migrations,
				dbMigrations
			});
			for (const migration of migrationsToRun) {
				for (const stmt of migration.sql) db.run(__sql_index_ts.sql.raw(stmt));
				db.run(__sql_index_ts.sql`INSERT INTO ${__sql_index_ts.sql.identifier(migrationsTable)} ("hash", "created_at", "name", "applied_at") VALUES(${migration.hash}, ${migration.folderMillis}, ${migration.name}, ${(/* @__PURE__ */ new Date()).toISOString()})`);
			}
			return;
		} catch (error) {
			tx.rollback();
			throw error;
		}
	});
}

//#endregion
exports.migrate = migrate;
//# sourceMappingURL=migrator.cjs.map