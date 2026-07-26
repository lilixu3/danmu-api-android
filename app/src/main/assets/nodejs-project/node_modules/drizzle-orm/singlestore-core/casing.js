import { singlestoreTableWithCasing } from "./table.js";
import { singlestoreSchema } from "./schema.js";

//#region src/singlestore-core/casing.ts
const snakeCase = {
	table: singlestoreTableWithCasing("snake_case"),
	schema: (name) => {
		return singlestoreSchema(name, "snake_case");
	}
};
const camelCase = {
	table: singlestoreTableWithCasing("camelCase"),
	schema: (name) => {
		return singlestoreSchema(name, "camelCase");
	}
};

//#endregion
export { camelCase, snakeCase };
//# sourceMappingURL=casing.js.map