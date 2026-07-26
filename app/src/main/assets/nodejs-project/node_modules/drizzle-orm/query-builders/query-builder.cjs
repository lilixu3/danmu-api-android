Object.defineProperty(exports, Symbol.toStringTag, { value: 'Module' });
const require_runtime = require('../_virtual/_rolldown/runtime.cjs');
let __entity_ts = require("../entity.cjs");

//#region src/query-builders/query-builder.ts
var TypedQueryBuilder = class {
	static [__entity_ts.entityKind] = "TypedQueryBuilder";
	/** @internal */
	getSelectedFields() {
		return this._.selectedFields;
	}
	/** @internal */
	withoutSelectionCastCodecs() {
		return this;
	}
};

//#endregion
exports.TypedQueryBuilder = TypedQueryBuilder;
//# sourceMappingURL=query-builder.cjs.map