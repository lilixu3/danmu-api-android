import { extractUsedTable } from "../utils.js";
import { PgInsertBase } from "../query-builders/insert.js";
import { entityKind } from "../../entity.js";
import * as Effect from "effect/Effect";
import { applyEffectWrapper } from "../../effect-core/query-effect.js";

//#region src/pg-core/effect/insert.ts
var PgEffectInsertBase = class extends PgInsertBase {
	static [entityKind] = "PgEffectInsert";
	/** @internal */
	_prepare(name, generateName = false) {
		const { session, config, dialect } = this;
		const { returning: fields } = config;
		const query = dialect.sqlToQuery(this.getSQL());
		const mapper = fields ? this.dialect.mapperGenerators.rows(fields, void 0) : void 0;
		return session.prepareQuery(query, fields ? "arrays" : "raw", name ?? generateName, mapper, {
			type: "insert",
			tables: [...extractUsedTable(this.config.table)]
		});
	}
	prepare(name) {
		return this._prepare(name, true);
	}
	execute = (placeholderValues) => {
		return this._prepare().execute(placeholderValues).pipe(Effect.withSpan("drizzle.operation"));
	};
};
applyEffectWrapper(PgEffectInsertBase);

//#endregion
export { PgEffectInsertBase };
//# sourceMappingURL=insert.js.map