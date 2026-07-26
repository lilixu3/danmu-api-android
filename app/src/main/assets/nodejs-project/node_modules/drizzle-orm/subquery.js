import { entityKind } from "./entity.js";

//#region src/subquery.ts
var Subquery = class {
	static [entityKind] = "Subquery";
	constructor(sql, fields, alias, isWith = false, usedTables = []) {
		this._ = {
			brand: "Subquery",
			sql,
			selectedFields: fields,
			alias,
			isWith,
			usedTables
		};
	}
};
var WithSubquery = class extends Subquery {
	static [entityKind] = "WithSubquery";
};

//#endregion
export { Subquery, WithSubquery };
//# sourceMappingURL=subquery.js.map