Object.defineProperty(exports, Symbol.toStringTag, { value: 'Module' });
const require_runtime = require('./_virtual/_rolldown/runtime.cjs');
const require_migrator_utils = require('./migrator.utils.cjs');
let node_crypto = require("node:crypto");
node_crypto = require_runtime.__toESM(node_crypto);
let node_fs = require("node:fs");
node_fs = require_runtime.__toESM(node_fs);
let node_path = require("node:path");

//#region src/migrator.ts
function readMigrationFiles(config) {
	if (node_fs.default.existsSync(`${config.migrationsFolder}/meta/_journal.json`)) throw Error("We detected that you have old drizzle-kit migration folders. You must upgrade drizzle-kit and run \"drizzle-kit up\"");
	const migrationFolderTo = config.migrationsFolder;
	const migrationQueries = [];
	const migrations = (0, node_fs.readdirSync)(migrationFolderTo).map((subdir) => ({
		path: (0, node_path.join)(migrationFolderTo, subdir, "migration.sql"),
		name: subdir
	})).filter((it) => (0, node_fs.existsSync)(it.path));
	migrations.sort((a, b) => a.name.localeCompare(b.name));
	for (const migration of migrations) {
		const migrationPath = migration.path;
		const migrationDate = migration.name.slice(0, 14);
		const query = node_fs.default.readFileSync(migrationPath).toString();
		const result = query.split("--> statement-breakpoint").map((it) => {
			return it;
		});
		const millis = require_migrator_utils.formatToMillis(migrationDate);
		migrationQueries.push({
			sql: result,
			bps: true,
			folderMillis: millis,
			hash: node_crypto.default.createHash("sha256").update(query).digest("hex"),
			name: migration.name
		});
	}
	return migrationQueries;
}

//#endregion
exports.readMigrationFiles = readMigrationFiles;
//# sourceMappingURL=migrator.cjs.map