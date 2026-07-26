import { PgCountBuilder } from "../query-builders/count.js";
import { entityKind } from "../../entity.js";
import { applyMixins } from "../../utils.js";
import { QueryPromise } from "../../query-promise.js";

//#region src/pg-core/async/count.ts
var PgAsyncCountBuilder = class extends PgCountBuilder {
	static [entityKind] = "PgAsyncCountBuilder";
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
applyMixins(PgAsyncCountBuilder, [QueryPromise]);

//#endregion
export { PgAsyncCountBuilder };
//# sourceMappingURL=count.js.map