import { PgCountBuilder } from "../query-builders/count.js";
import { entityKind } from "../../entity.js";
import { applyEffectWrapper } from "../../effect-core/query-effect.js";

//#region src/pg-core/effect/count.ts
var PgEffectCountBuilder = class extends PgCountBuilder {
	static [entityKind] = "PgEffectCountBuilder";
	session;
	constructor({ source, dialect, filters, session }) {
		super({
			source,
			dialect,
			filters
		});
		this.session = session;
	}
	execute(placeholderValues) {
		return this.session.prepareQuery(this.build(), "arrays", false, (rows) => {
			const v = rows[0]?.[0];
			if (typeof v === "number") return v;
			return v ? Number(v) : 0;
		}).execute(placeholderValues);
	}
};
applyEffectWrapper(PgEffectCountBuilder);

//#endregion
export { PgEffectCountBuilder };
//# sourceMappingURL=count.js.map