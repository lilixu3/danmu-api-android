Object.defineProperty(exports, Symbol.toStringTag, { value: 'Module' });
const require_runtime = require('../_virtual/_rolldown/runtime.cjs');
let __sql_sql_ts = require("../sql/sql.cjs");
let __sql_expressions_index_ts = require("../sql/expressions/index.cjs");

//#region src/singlestore-core/expressions.ts
function concat(column, value) {
	return __sql_sql_ts.sql`${column} || ${(0, __sql_expressions_index_ts.bindIfParam)(value, column)}`;
}
function substring(column, { from, for: _for }) {
	const chunks = [__sql_sql_ts.sql`substring(`, column];
	if (from !== void 0) chunks.push(__sql_sql_ts.sql` from `, (0, __sql_expressions_index_ts.bindIfParam)(from, column));
	if (_for !== void 0) chunks.push(__sql_sql_ts.sql` for `, (0, __sql_expressions_index_ts.bindIfParam)(_for, column));
	chunks.push(__sql_sql_ts.sql`)`);
	return __sql_sql_ts.sql.join(chunks);
}
function dotProduct(column, value) {
	return __sql_sql_ts.sql`${column} <*> ${JSON.stringify(value)}`;
}
function euclideanDistance(column, value) {
	return __sql_sql_ts.sql`${column} <-> ${JSON.stringify(value)}`;
}

//#endregion
exports.concat = concat;
exports.dotProduct = dotProduct;
exports.euclideanDistance = euclideanDistance;
exports.substring = substring;
Object.keys(__sql_expressions_index_ts).forEach(function (k) {
  if (k !== 'default' && !Object.prototype.hasOwnProperty.call(exports, k)) Object.defineProperty(exports, k, {
    enumerable: true,
    get: function () { return __sql_expressions_index_ts[k]; }
  });
});

//# sourceMappingURL=expressions.cjs.map