import { entityKind } from "../entity.js";

//#region src/query-builders/query-builder.ts
var TypedQueryBuilder = class {
	static [entityKind] = "TypedQueryBuilder";
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
export { TypedQueryBuilder };
//# sourceMappingURL=query-builder.js.map