import { extractUsedTable } from "../utils.js";
import { PgUpdateBase } from "../query-builders/update.js";
import { entityKind } from "../../entity.js";
import { applyEffectWrapper } from "../../effect-core/query-effect.js";

//#region src/pg-core/effect/update.ts
var PgEffectUpdateBase = class extends PgUpdateBase {
	static [entityKind] = "PgEffectUpdate";
	/** @internal */
	_prepare(name, generateName = false) {
		const { session, config, dialect, joinsNotNullableMap } = this;
		const { returning: fields } = config;
		const query = dialect.sqlToQuery(this.getSQL());
		const mapper = fields ? this.dialect.mapperGenerators.rows(fields, joinsNotNullableMap) : void 0;
		return session.prepareQuery(query, fields ? "arrays" : "raw", name ?? generateName, mapper, {
			type: "update",
			tables: [...extractUsedTable(this.config.table)]
		});
	}
	prepare(name) {
		return this._prepare(name, true);
	}
	execute = (placeholderValues = {}) => {
		return this._prepare().execute(placeholderValues);
	};
};
applyEffectWrapper(PgEffectUpdateBase);

//#endregion
export { PgEffectUpdateBase };
//# sourceMappingURL=update.js.map