Object.defineProperty(exports, Symbol.toStringTag, { value: 'Module' });
const require_runtime = require('../_virtual/_rolldown/runtime.cjs');
let __sql_sql_ts = require("../sql/sql.cjs");
let __pg_core_index_ts = require("../pg-core/index.cjs");
let __pg_core_roles_ts = require("../pg-core/roles.cjs");

//#region src/supabase/rls.ts
const anonRole = (0, __pg_core_roles_ts.pgRole)("anon").existing();
const authenticatedRole = (0, __pg_core_roles_ts.pgRole)("authenticated").existing();
const serviceRole = (0, __pg_core_roles_ts.pgRole)("service_role").existing();
const postgresRole = (0, __pg_core_roles_ts.pgRole)("postgres_role").existing();
const supabaseAuthAdminRole = (0, __pg_core_roles_ts.pgRole)("supabase_auth_admin").existing();
const auth = (0, __pg_core_index_ts.pgSchema)("auth");
const authUsers = auth.table("users", {
	id: (0, __pg_core_index_ts.uuid)().primaryKey().notNull(),
	email: (0, __pg_core_index_ts.varchar)({ length: 255 }),
	phone: (0, __pg_core_index_ts.text)().unique(),
	emailConfirmedAt: (0, __pg_core_index_ts.timestamp)("email_confirmed_at", { withTimezone: true }),
	phoneConfirmedAt: (0, __pg_core_index_ts.timestamp)("phone_confirmed_at", { withTimezone: true }),
	lastSignInAt: (0, __pg_core_index_ts.timestamp)("last_sign_in_at", { withTimezone: true }),
	createdAt: (0, __pg_core_index_ts.timestamp)("created_at", { withTimezone: true }),
	updatedAt: (0, __pg_core_index_ts.timestamp)("updated_at", { withTimezone: true })
});
const realtime = (0, __pg_core_index_ts.pgSchema)("realtime");
const realtimeMessages = realtime.table("messages", {
	id: (0, __pg_core_index_ts.bigserial)({ mode: "bigint" }).primaryKey(),
	topic: (0, __pg_core_index_ts.text)().notNull(),
	extension: (0, __pg_core_index_ts.text)({ enum: [
		"presence",
		"broadcast",
		"postgres_changes"
	] }).notNull()
});
const authUid = __sql_sql_ts.sql`(select auth.uid())`;
const realtimeTopic = __sql_sql_ts.sql`realtime.topic()`;

//#endregion
exports.anonRole = anonRole;
exports.authUid = authUid;
exports.authUsers = authUsers;
exports.authenticatedRole = authenticatedRole;
exports.postgresRole = postgresRole;
exports.realtimeMessages = realtimeMessages;
exports.realtimeTopic = realtimeTopic;
exports.serviceRole = serviceRole;
exports.supabaseAuthAdminRole = supabaseAuthAdminRole;
//# sourceMappingURL=rls.cjs.map