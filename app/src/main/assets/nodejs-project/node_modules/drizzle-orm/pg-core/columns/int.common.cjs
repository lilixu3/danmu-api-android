Object.defineProperty(exports, Symbol.toStringTag, { value: 'Module' });
const require_runtime = require('../../_virtual/_rolldown/runtime.cjs');
const require_pg_core_columns_common = require('./common.cjs');
let __entity_ts = require("../../entity.cjs");

//#region src/pg-core/columns/int.common.ts
var PgIntColumnBuilder = class extends require_pg_core_columns_common.PgColumnBuilder {
	static [__entity_ts.entityKind] = "PgIntColumnBaseBuilder";
	/**
	* Adds an `ALWAYS AS IDENTITY` clause to the column definition.
	* Available for integer column types.
	*/
	generatedAlwaysAsIdentity(sequence) {
		if (sequence) {
			const { name, ...options } = sequence;
			this.config.generatedIdentity = {
				type: "always",
				sequenceName: name,
				sequenceOptions: options
			};
		} else this.config.generatedIdentity = { type: "always" };
		this.config.hasDefault = true;
		this.config.notNull = true;
		return this;
	}
	/**
	* Adds a `BY DEFAULT AS IDENTITY` clause to the column definition.
	* Available for integer column types.
	*/
	generatedByDefaultAsIdentity(sequence) {
		if (sequence) {
			const { name, ...options } = sequence;
			this.config.generatedIdentity = {
				type: "byDefault",
				sequenceName: name,
				sequenceOptions: options
			};
		} else this.config.generatedIdentity = { type: "byDefault" };
		this.config.hasDefault = true;
		this.config.notNull = true;
		return this;
	}
};

//#endregion
exports.PgIntColumnBuilder = PgIntColumnBuilder;
//# sourceMappingURL=int.common.cjs.map