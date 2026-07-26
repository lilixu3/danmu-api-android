import { is } from "../entity.js";
import { sql } from "../sql/sql.js";
import { pgPolicy } from "../pg-core/index.js";
import { PgRole, pgRole } from "../pg-core/roles.js";

//#region src/neon/rls.ts
/**
* Generates a set of PostgreSQL row-level security (RLS) policies for CRUD operations based on the provided options.
*
* @param options - An object containing the policy configuration.
* @param options.role - The PostgreSQL role(s) to apply the policy to. Can be a single `PgRole` instance or an array of `PgRole` instances or role names.
* @param options.read - The SQL expression or boolean value that defines the read policy. Set to `true` to allow all reads, `false` to deny all reads, or provide a custom SQL expression. Set to `null` to prevent the policy from being generated.
* @param options.modify - The SQL expression or boolean value that defines the modify (insert, update, delete) policies. Set to `true` to allow all modifications, `false` to deny all modifications, or provide a custom SQL expression. Set to `null` to prevent policies from being generated.
* @returns An array of PostgreSQL policy definitions, one for each CRUD operation.
*/
const crudPolicy = (options) => {
	if (options.read === void 0) throw new Error("crudPolicy requires a read policy");
	if (options.modify === void 0) throw new Error("crudPolicy requires a modify policy");
	let read;
	if (options.read === true) read = sql`true`;
	else if (options.read === false) read = sql`false`;
	else if (options.read !== null) read = options.read;
	let modify;
	if (options.modify === true) modify = sql`true`;
	else if (options.modify === false) modify = sql`false`;
	else if (options.modify !== null) modify = options.modify;
	let rolesName = "";
	if (Array.isArray(options.role)) rolesName = options.role.map((it) => {
		return is(it, PgRole) ? it.name : it;
	}).join("-");
	else rolesName = is(options.role, PgRole) ? options.role.name : options.role;
	return [
		read && pgPolicy(`crud-${rolesName}-policy-select`, {
			for: "select",
			to: options.role,
			using: read
		}),
		modify && pgPolicy(`crud-${rolesName}-policy-insert`, {
			for: "insert",
			to: options.role,
			withCheck: modify
		}),
		modify && pgPolicy(`crud-${rolesName}-policy-update`, {
			for: "update",
			to: options.role,
			using: modify,
			withCheck: modify
		}),
		modify && pgPolicy(`crud-${rolesName}-policy-delete`, {
			for: "delete",
			to: options.role,
			using: modify
		})
	].filter(Boolean);
};
const authenticatedRole = pgRole("authenticated").existing();
const anonymousRole = pgRole("anonymous").existing();
const authUid = (userIdColumn) => sql`(select auth.user_id() = ${userIdColumn})`;

//#endregion
export { anonymousRole, authUid, authenticatedRole, crudPolicy };
//# sourceMappingURL=rls.js.map