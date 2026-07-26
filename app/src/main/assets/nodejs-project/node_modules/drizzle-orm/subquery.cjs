Object.defineProperty(exports, Symbol.toStringTag, { value: 'Module' });
const require_entity = require('./entity.cjs');

//#region src/subquery.ts
var Subquery = class {
	static [require_entity.entityKind] = "Subquery";
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
	static [require_entity.entityKind] = "WithSubquery";
};

//#endregion
exports.Subquery = Subquery;
exports.WithSubquery = WithSubquery;
//# sourceMappingURL=subquery.cjs.map