import { entityKind } from "./entity.js";

//#region src/primary-key.ts
var PrimaryKey = class {
	static [entityKind] = "PrimaryKey";
	constructor(table, columns) {
		this.table = table;
		this.columns = columns;
	}
};

//#endregion
export { PrimaryKey };
//# sourceMappingURL=primary-key.js.map