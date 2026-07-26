import { mysqlTableWithCasing } from "./table.js";
import { mysqlViewWithCasing } from "./view.js";
import { mysqlSchema } from "./schema.js";

//#region src/mysql-core/casing.ts
const snakeCase = {
	table: mysqlTableWithCasing("snake_case"),
	view: mysqlViewWithCasing("snake_case"),
	schema: (name) => {
		return mysqlSchema(name, "snake_case");
	}
};
const camelCase = {
	table: mysqlTableWithCasing("camelCase"),
	view: mysqlViewWithCasing("camelCase"),
	schema: (name) => {
		return mysqlSchema(name, "camelCase");
	}
};

//#endregion
export { camelCase, snakeCase };
//# sourceMappingURL=casing.js.map