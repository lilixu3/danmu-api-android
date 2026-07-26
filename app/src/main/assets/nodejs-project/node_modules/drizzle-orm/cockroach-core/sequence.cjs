Object.defineProperty(exports, Symbol.toStringTag, { value: 'Module' });
const require_runtime = require('../_virtual/_rolldown/runtime.cjs');
let __entity_ts = require("../entity.cjs");

//#region src/cockroach-core/sequence.ts
var CockroachSequence = class {
	static [__entity_ts.entityKind] = "CockroachSequence";
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
	return (0, __entity_ts.is)(obj, CockroachSequence);
}

//#endregion
exports.CockroachSequence = CockroachSequence;
exports.cockroachSequence = cockroachSequence;
exports.cockroachSequenceWithSchema = cockroachSequenceWithSchema;
exports.isCockroachSequence = isCockroachSequence;
//# sourceMappingURL=sequence.cjs.map