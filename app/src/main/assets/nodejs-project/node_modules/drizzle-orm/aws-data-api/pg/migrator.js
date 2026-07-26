import { readMigrationFiles } from "../../migrator.js";
import { migrate as migrate$1 } from "../../pg-core/async/session.js";

//#region src/aws-data-api/pg/migrator.ts
async function migrate(db, config) {
	return await migrate$1(readMigrationFiles(config), db, config);
}

//#endregion
export { migrate };
//# sourceMappingURL=migrator.js.map