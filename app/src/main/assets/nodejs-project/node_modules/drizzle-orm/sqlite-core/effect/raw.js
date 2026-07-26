import { entityKind } from "../../entity.js";
import { applyEffectWrapper } from "../../effect-core/query-effect.js";
import { SQLiteRaw } from "../query-builders/raw.js";

//#region src/sqlite-core/effect/raw.ts
var SQLiteEffectRaw = class extends SQLiteRaw {
	static [entityKind] = "SQLiteEffectRaw";
	constructor(prepared, sql, query) {
		super(prepared, sql, query);
	}
	execute(placeholderValues) {
		return this.prepared.execute(placeholderValues);
	}
};
applyEffectWrapper(SQLiteEffectRaw);

//#endregion
export { SQLiteEffectRaw };
//# sourceMappingURL=raw.js.map