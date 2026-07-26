import { migrate as migrate$1 } from "./mysql/migrator.js";
import { migrate as migrate$2 } from "./postgres/migrator.js";
import { migrate as migrate$3 } from "./sqlite/migrator.js";

//#region src/bun-sql/migrator.ts
async function migrate(db, config) {
	return migrate$2(db, config);
}
(function(_migrate) {
	async function postgres(db, config) {
		return migrate$2(db, config);
	}
	_migrate.postgres = postgres;
	async function sqlite(db, config) {
		return migrate$3(db, config);
	}
	_migrate.sqlite = sqlite;
	async function mysql(db, config) {
		return migrate$1(db, config);
	}
	_migrate.mysql = mysql;
})(migrate || (migrate = {}));

//#endregion
export { migrate };
//# sourceMappingURL=migrator.js.map