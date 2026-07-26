import { sql } from "../sql/sql.js";
import { bindIfParam } from "../sql/expressions/index.js";

export * from "../sql/expressions/index.js"

//#region src/mssql-core/expressions.ts
function concat(column, value) {
	return sql`${column} || ${bindIfParam(value, column)}`;
}
function substring(column, { from, for: _for }) {
	const chunks = [sql`substring(`, column];
	if (from !== void 0) chunks.push(sql` from `, bindIfParam(from, column));
	if (_for !== void 0) chunks.push(sql` for `, bindIfParam(_for, column));
	chunks.push(sql`)`);
	return sql.join(chunks);
}

//#endregion
export { concat, substring };
//# sourceMappingURL=expressions.js.map