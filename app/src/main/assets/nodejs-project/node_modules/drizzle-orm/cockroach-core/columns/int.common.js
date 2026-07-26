import { CockroachColumnWithArrayBuilder } from "./common.js";
import { entityKind } from "../../entity.js";

//#region src/cockroach-core/columns/int.common.ts
var CockroachIntColumnBaseBuilder = class extends CockroachColumnWithArrayBuilder {
	static [entityKind] = "CockroachIntColumnBaseBuilder";
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
export { CockroachIntColumnBaseBuilder };
//# sourceMappingURL=int.common.js.map