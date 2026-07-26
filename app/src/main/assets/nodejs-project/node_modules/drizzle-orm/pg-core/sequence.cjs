Object.defineProperty(exports, Symbol.toStringTag, { value: 'Module' });
const require_runtime = require('../_virtual/_rolldown/runtime.cjs');
let __entity_ts = require("../entity.cjs");

//#region src/pg-core/sequence.ts
var PgSequence = class {
	static [__entity_ts.entityKind] = "PgSequence";
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
	return (0, __entity_ts.is)(obj, PgSequence);
}

//#endregion
exports.PgSequence = PgSequence;
exports.isPgSequence = isPgSequence;
exports.pgSequence = pgSequence;
exports.pgSequenceWithSchema = pgSequenceWithSchema;
//# sourceMappingURL=sequence.cjs.map