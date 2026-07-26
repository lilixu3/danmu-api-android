Object.defineProperty(exports, Symbol.toStringTag, { value: 'Module' });
const require_runtime = require('../_virtual/_rolldown/runtime.cjs');
let __entity_ts = require("../entity.cjs");
let __sql_sql_ts = require("../sql/sql.cjs");
let __pg_core_index_ts = require("../pg-core/index.cjs");
let __pg_core_roles_ts = require("../pg-core/roles.cjs");

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
	if (options.read === true) read = __sql_sql_ts.sql`true`;
	else if (options.read === false) read = __sql_sql_ts.sql`false`;
	else if (options.read !== null) read = options.read;
	let modify;
	if (options.modify === true) modify = __sql_sql_ts.sql`true`;
	else if (options.modify === false) modify = __sql_sql_ts.sql`false`;
	else if (options.modify !== null) modify = options.modify;
	let rolesName = "";
	if (Array.isArray(options.role)) rolesName = options.role.map((it) => {
		return (0, __entity_ts.is)(it, __pg_core_roles_ts.PgRole) ? it.name : it;
	}).join("-");
	else rolesName = (0, __entity_ts.is)(options.role, __pg_core_roles_ts.PgRole) ? options.role.name : options.role;
	return [
		read && (0, __pg_core_index_ts.pgPolicy)(`crud-${rolesName}-policy-select`, {
			for: "select",
			to: options.role,
			using: read
		}),
		modify && (0, __pg_core_index_ts.pgPolicy)(`crud-${rolesName}-policy-insert`, {
			for: "insert",
			to: options.role,
			withCheck: modify
		}),
		modify && (0, __pg_core_index_ts.pgPolicy)(`crud-${rolesName}-policy-update`, {
			for: "update",
			to: options.role,
			using: modify,
			withCheck: modify
		}),
		modify && (0, __pg_core_index_ts.pgPolicy)(`crud-${rolesName}-policy-delete`, {
			for: "delete",
			to: options.role,
			using: modify
		})
	].filter(Boolean);
};
const authenticatedRole = (0, __pg_core_roles_ts.pgRole)("authenticated").existing();
const anonymousRole = (0, __pg_core_roles_ts.pgRole)("anonymous").existing();
const authUid = (userIdColumn) => __sql_sql_ts.sql`(select auth.user_id() = ${userIdColumn})`;

//#endregion
exports.anonymousRole = anonymousRole;
exports.authUid = authUid;
exports.authenticatedRole = authenticatedRole;
exports.crudPolicy = crudPolicy;
//# sourceMappingURL=rls.cjs.map