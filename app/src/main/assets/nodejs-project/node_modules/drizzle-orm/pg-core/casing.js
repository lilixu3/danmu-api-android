import { pgTableWithCasing } from "./table.js";
import { pgMaterializedViewWithCasing, pgViewWithCasing } from "./view.js";
import { pgSchema } from "./schema.js";

//#region src/pg-core/casing.ts
const snakeCase = {
	table: pgTableWithCasing("snake_case"),
	view: pgViewWithCasing("snake_case"),
	materializedView: pgMaterializedViewWithCasing("snake_case"),
	schema: (name) => {
		return pgSchema(name, "snake_case");
	}
};
const camelCase = {
	table: pgTableWithCasing("camelCase"),
	view: pgViewWithCasing("camelCase"),
	materializedView: pgMaterializedViewWithCasing("camelCase"),
	schema: (name) => {
		return pgSchema(name, "camelCase");
	}
};

//#endregion
export { camelCase, snakeCase };
//# sourceMappingURL=casing.js.map