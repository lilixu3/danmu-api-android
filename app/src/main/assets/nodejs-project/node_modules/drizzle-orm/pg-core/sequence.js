import { entityKind, is } from "../entity.js";

//#region src/pg-core/sequence.ts
var PgSequence = class {
	static [entityKind] = "PgSequence";
	constructor(seqName, seqOptions, schema) {
		this.seqName = seqName;
		this.seqOptions = seqOptions;
		this.schema = schema;
	}
};
function pgSequence(name, options) {
	return pgSequenceWithSchema(name, options, void 0);
}
/** @internal */
function pgSequenceWithSchema(name, options, schema) {
	return new PgSequence(name, options, schema);
}
function isPgSequence(obj) {
	return is(obj, PgSequence);
}

//#endregion
export { PgSequence, isPgSequence, pgSequence, pgSequenceWithSchema };
//# sourceMappingURL=sequence.js.map