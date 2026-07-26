import { PgRaw } from "../query-builders/raw.js";
import { entityKind } from "../../entity.js";
import { applyEffectWrapper } from "../../effect-core/query-effect.js";

//#region src/pg-core/effect/raw.ts
var PgEffectRaw = class extends PgRaw {
	static [entityKind] = "PgEffectRaw";
	constructor(prepared, sql, query) {
		super(prepared, sql, query);
	}
	execute(placeholderValues) {
		return this.prepared.execute(placeholderValues);
	}
	_prepare() {
		return this.prepared;
	}
};
applyEffectWrapper(PgEffectRaw);

//#endregion
export { PgEffectRaw };
//# sourceMappingURL=raw.js.map