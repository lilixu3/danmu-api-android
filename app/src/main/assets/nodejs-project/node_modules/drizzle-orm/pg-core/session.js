import { entityKind } from "../entity.js";

//#region src/pg-core/session.ts
var PgBasePreparedQuery = class {
	static [entityKind] = "PgBasePreparedQuery";
	constructor(query) {
		this.query = query;
	}
	getQuery() {
		return this.query;
	}
};
var PgSession = class {
	static [entityKind] = "PgSession";
	constructor(dialect) {
		this.dialect = dialect;
	}
};

//#endregion
export { PgBasePreparedQuery, PgSession };
//# sourceMappingURL=session.js.map