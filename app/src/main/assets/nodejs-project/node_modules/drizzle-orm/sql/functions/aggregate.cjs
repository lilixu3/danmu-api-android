Object.defineProperty(exports, Symbol.toStringTag, { value: 'Module' });
const require_runtime = require('../../_virtual/_rolldown/runtime.cjs');
const require_sql_sql = require('../sql.cjs');
let __entity_ts = require("../../entity.cjs");
let __column_ts = require("../../column.cjs");

//#region src/sql/functions/aggregate.ts
/**
* Returns the number of values in `expression`.
*
* ## Examples
*
* ```ts
* // Number employees with null values
* db.select({ value: count() }).from(employees)
* // Number of employees where `name` is not null
* db.select({ value: count(employees.name) }).from(employees)
* ```
*
* @see countDistinct to get the number of non-duplicate values in `expression`
*/
function count(expression) {
	return require_sql_sql.sql`count(${expression || require_sql_sql.sql.raw("*")})`.mapWith(Number);
}
/**
* Returns the number of non-duplicate values in `expression`.
*
* ## Examples
*
* ```ts
* // Number of employees where `name` is distinct
* db.select({ value: countDistinct(employees.name) }).from(employees)
* ```
*
* @see count to get the number of values in `expression`, including duplicates
*/
function countDistinct(expression) {
	return require_sql_sql.sql`count(distinct ${expression})`.mapWith(Number);
}
/**
* Returns the average (arithmetic mean) of all non-null values in `expression`.
*
* ## Examples
*
* ```ts
* // Average salary of an employee
* db.select({ value: avg(employees.salary) }).from(employees)
* ```
*
* @see avgDistinct to get the average of all non-null and non-duplicate values in `expression`
*/
function avg(expression) {
	return require_sql_sql.sql`avg(${expression})`.mapWith(String);
}
/**
* Returns the average (arithmetic mean) of all non-null and non-duplicate values in `expression`.
*
* ## Examples
*
* ```ts
* // Average salary of an employee where `salary` is distinct
* db.select({ value: avgDistinct(employees.salary) }).from(employees)
* ```
*
* @see avg to get the average of all non-null values in `expression`, including duplicates
*/
function avgDistinct(expression) {
	return require_sql_sql.sql`avg(distinct ${expression})`.mapWith(String);
}
/**
* Returns the sum of all non-null values in `expression`.
*
* ## Examples
*
* ```ts
* // Sum of every employee's salary
* db.select({ value: sum(employees.salary) }).from(employees)
* ```
*
* @see sumDistinct to get the sum of all non-null and non-duplicate values in `expression`
*/
function sum(expression) {
	return require_sql_sql.sql`sum(${expression})`.mapWith(String);
}
/**
* Returns the sum of all non-null and non-duplicate values in `expression`.
*
* ## Examples
*
* ```ts
* // Sum of every employee's salary where `salary` is distinct (no duplicates)
* db.select({ value: sumDistinct(employees.salary) }).from(employees)
* ```
*
* @see sum to get the sum of all non-null values in `expression`, including duplicates
*/
function sumDistinct(expression) {
	return require_sql_sql.sql`sum(distinct ${expression})`.mapWith(String);
}
/**
* Returns the maximum value in `expression`.
*
* ## Examples
*
* ```ts
* // The employee with the highest salary
* db.select({ value: max(employees.salary) }).from(employees)
* ```
*/
function max(expression) {
	return require_sql_sql.sql`max(${expression})`.mapWith((0, __entity_ts.is)(expression, __column_ts.Column) ? expression : String);
}
/**
* Returns the minimum value in `expression`.
*
* ## Examples
*
* ```ts
* // The employee with the lowest salary
* db.select({ value: min(employees.salary) }).from(employees)
* ```
*/
function min(expression) {
	return require_sql_sql.sql`min(${expression})`.mapWith((0, __entity_ts.is)(expression, __column_ts.Column) ? expression : String);
}

//#endregion
exports.avg = avg;
exports.avgDistinct = avgDistinct;
exports.count = count;
exports.countDistinct = countDistinct;
exports.max = max;
exports.min = min;
exports.sum = sum;
exports.sumDistinct = sumDistinct;
//# sourceMappingURL=aggregate.cjs.map