import { sqliteTableWithCasing } from "./table.js";
import { sqliteViewWithCasing } from "./view.js";

//#region src/sqlite-core/casing.ts
const snakeCase = {
	table: sqliteTableWithCasing("snake_case"),
	view: sqliteViewWithCasing("snake_case")
};
const camelCase = {
	table: sqliteTableWithCasing("camelCase"),
	view: sqliteViewWithCasing("camelCase")
};

//#endregion
export { camelCase, snakeCase };
//# sourceMappingURL=casing.js.map