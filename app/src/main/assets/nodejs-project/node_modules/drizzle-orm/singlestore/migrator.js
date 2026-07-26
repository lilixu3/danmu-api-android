import { readMigrationFiles } from "../migrator.js";

//#region src/singlestore/migrator.ts
async function migrate(db, config) {
	const migrations = readMigrationFiles(config);
	return await db.dialect.migrate(migrations, db.session, config);
}

//#endregion
export { migrate };
//# sourceMappingURL=migrator.js.map