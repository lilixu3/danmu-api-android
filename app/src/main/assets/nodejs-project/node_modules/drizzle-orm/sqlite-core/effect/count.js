import { entityKind } from "../../entity.js";
import { applyEffectWrapper } from "../../effect-core/query-effect.js";
import { SQLiteCountBuilder } from "../query-builders/count.js";

//#region src/sqlite-core/effect/count.ts
var SQLiteEffectCountBuilder = class extends SQLiteCountBuilder {
	static [entityKind] = "SQLiteEffectCountBuilder";
	constructor(countConfig) {
		super(countConfig);
	}
	execute(placeholderValues) {
		return this.session.prepareQuery(this.build(), "arrays", false, "all", (rows) => {
			const v = rows[0]?.[0];
			if (typeof v === "number") return v;
			return v ? Number(v) : 0;
		}).execute(placeholderValues);
	}
};
applyEffectWrapper(SQLiteEffectCountBuilder);

//#endregion
export { SQLiteEffectCountBuilder };
//# sourceMappingURL=count.js.map