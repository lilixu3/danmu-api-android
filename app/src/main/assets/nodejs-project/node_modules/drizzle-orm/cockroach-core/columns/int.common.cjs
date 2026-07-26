Object.defineProperty(exports, Symbol.toStringTag, { value: 'Module' });
const require_runtime = require('../../_virtual/_rolldown/runtime.cjs');
const require_cockroach_core_columns_common = require('./common.cjs');
let __entity_ts = require("../../entity.cjs");

//#region src/cockroach-core/columns/int.common.ts
var CockroachIntColumnBaseBuilder = class extends require_cockroach_core_columns_common.CockroachColumnWithArrayBuilder {
	static [__entity_ts.entityKind] = "CockroachIntColumnBaseBuilder";
	generatedAlwaysAsIdentity(sequence) {
		this.config.generatedIdentity = sequence ? {
			type: "always",
			sequenceOptions: sequence
		} : { type: "always" };
		this.config.hasDefault = true;
		this.config.notNull = true;
		return this;
	}
	generatedByDefaultAsIdentity(sequence) {
		this.config.generatedIdentity = sequence ? {
			type: "byDefault",
			sequenceOptions: sequence
		} : { type: "byDefault" };
		this.config.hasDefault = true;
		this.config.notNull = true;
		return this;
	}
};

//#endregion
exports.CockroachIntColumnBaseBuilder = CockroachIntColumnBaseBuilder;
//# sourceMappingURL=int.common.cjs.map