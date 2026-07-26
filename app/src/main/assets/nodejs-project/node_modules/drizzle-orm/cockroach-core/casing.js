import { cockroachTableWithCasing } from "./table.js";
import { cockroachMaterializedViewWithCasing, cockroachViewWithCasing } from "./view.js";
import { cockroachSchema } from "./schema.js";

//#region src/cockroach-core/casing.ts
const snakeCase = {
	table: cockroachTableWithCasing("snake_case"),
	view: cockroachViewWithCasing("snake_case"),
	materializedView: cockroachMaterializedViewWithCasing("snake_case"),
	schema: (name) => {
		return cockroachSchema(name, "snake_case");
	}
};
const camelCase = {
	table: cockroachTableWithCasing("camelCase"),
	view: cockroachViewWithCasing("camelCase"),
	materializedView: cockroachMaterializedViewWithCasing("camelCase"),
	schema: (name) => {
		return cockroachSchema(name, "camelCase");
	}
};

//#endregion
export { camelCase, snakeCase };
//# sourceMappingURL=casing.js.map