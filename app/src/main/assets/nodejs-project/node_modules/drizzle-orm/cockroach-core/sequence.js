import { entityKind, is } from "../entity.js";

//#region src/cockroach-core/sequence.ts
var CockroachSequence = class {
	static [entityKind] = "CockroachSequence";
	constructor(seqName, seqOptions, schema) {
		this.seqName = seqName;
		this.seqOptions = seqOptions;
		this.schema = schema;
	}
};
function cockroachSequence(name, options) {
	return cockroachSequenceWithSchema(name, options, void 0);
}
/** @internal */
function cockroachSequenceWithSchema(name, options, schema) {
	return new CockroachSequence(name, options, schema);
}
function isCockroachSequence(obj) {
	return is(obj, CockroachSequence);
}

//#endregion
export { CockroachSequence, cockroachSequence, cockroachSequenceWithSchema, isCockroachSequence };
//# sourceMappingURL=sequence.js.map