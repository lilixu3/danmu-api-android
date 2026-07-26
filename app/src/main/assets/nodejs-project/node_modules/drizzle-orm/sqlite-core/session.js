import { entityKind } from "../entity.js";

//#region src/sqlite-core/session.ts
var SQLitePreparedQuery = class {
	static [entityKind] = "SQLiteBasePreparedQuery";
	/** @internal */
	mapper;
	/** @internal */
	executeMethod;
	constructor(executeMethod, query, mapper, mode) {
		this.query = query;
		this.mode = mode;
		this.mapper = mapper;
		this.executeMethod = executeMethod;
	}
	getQuery() {
		return this.query;
	}
};
var SQLiteSession = class {
	static [entityKind] = "SQLiteSession";
	constructor(dialect) {
		this.dialect = dialect;
	}
};

//#endregion
export { SQLitePreparedQuery, SQLiteSession };
//# sourceMappingURL=session.js.map