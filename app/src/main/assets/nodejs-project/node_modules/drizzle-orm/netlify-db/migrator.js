import { NodePgDatabase } from "../node-postgres/driver.js";
import { is } from "../entity.js";
import { readMigrationFiles } from "../migrator.js";
import { migrate as migrate$1 } from "../pg-core/async/session.js";
import { migrate as migrate$2 } from "../node-postgres/migrator.js";

//#region src/netlify-db/migrator.ts
async function migrate(db, config) {
	if (is(db, NodePgDatabase)) return migrate$2(db, config);
	return migrate$1(readMigrationFiles(config), db, config);
}

//#endregion
export { migrate };
//# sourceMappingURL=migrator.js.map