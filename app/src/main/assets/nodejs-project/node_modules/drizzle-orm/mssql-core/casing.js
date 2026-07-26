import { mssqlTableWithCasing } from "./table.js";
import { mssqlViewWithCasing } from "./view.js";
import { mssqlSchema } from "./schema.js";

//#region src/mssql-core/casing.ts
const snakeCase = {
	table: mssqlTableWithCasing("snake_case"),
	view: mssqlViewWithCasing("snake_case"),
	schema: (name) => {
		return mssqlSchema(name, "snake_case");
	}
};
const camelCase = {
	table: mssqlTableWithCasing("camelCase"),
	view: mssqlViewWithCasing("camelCase"),
	schema: (name) => {
		return mssqlSchema(name, "camelCase");
	}
};

//#endregion
export { camelCase, snakeCase };
//# sourceMappingURL=casing.js.map