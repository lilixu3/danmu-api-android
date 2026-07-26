import { readMigrationFiles } from "../migrator.js";
import { migrate as migrate$1 } from "../pg-core/effect/session.js";

//#region src/effect-pglite/migrator.ts
function migrate(db, config) {
	return migrate$1(readMigrationFiles(config), db.session, config);
}

//#endregion
export { migrate };
//# sourceMappingURL=migrator.js.map