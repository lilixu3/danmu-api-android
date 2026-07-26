Object.defineProperty(exports, Symbol.toStringTag, { value: 'Module' });
const require_runtime = require('./_virtual/_rolldown/runtime.cjs');
let __entity_ts = require("./entity.cjs");

//#region src/primary-key.ts
var PrimaryKey = class {
	static [__entity_ts.entityKind] = "PrimaryKey";
	constructor(table, columns) {
		this.table = table;
		this.columns = columns;
	}
};

//#endregion
exports.PrimaryKey = PrimaryKey;
//# sourceMappingURL=primary-key.cjs.map