import { PgColumnBuilder } from "./common.js";
import { entityKind } from "../../entity.js";

//#region src/pg-core/columns/int.common.ts
var PgIntColumnBuilder = class extends PgColumnBuilder {
	static [entityKind] = "PgIntColumnBaseBuilder";
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
export { PgIntColumnBuilder };
//# sourceMappingURL=int.common.js.map