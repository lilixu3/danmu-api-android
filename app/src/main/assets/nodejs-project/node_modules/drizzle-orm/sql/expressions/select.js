import { sql } from "../sql.js";

//#region src/sql/expressions/select.ts
/**
* Used in sorting, this specifies that the given
* column or expression should be sorted in ascending
* order. By the SQL standard, ascending order is the
* default, so it is not usually necessary to specify
* ascending sort order.
*
* ## Examples
*
* ```ts
* // Return cars, starting with the oldest models
* // and going in ascending order to the newest.
* db.select().from(cars)
*   .orderBy(asc(cars.year));
* ```
*
* @see desc to sort in descending order
*/
function asc(column) {
	return sql`${column} asc`;
}
/**
* Used in sorting, this specifies that the given
* column or expression should be sorted in descending
* order.
*
* ## Examples
*
* ```ts
* // Select users, with the most recently created
* // records coming first.
* db.select().from(users)
*   .orderBy(desc(users.createdAt));
* ```
*
* @see asc to sort in ascending order
*/
function desc(column) {
	return sql`${column} desc`;
}

//#endregion
export { asc, desc };
//# sourceMappingURL=select.js.map