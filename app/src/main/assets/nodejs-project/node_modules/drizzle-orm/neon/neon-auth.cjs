Object.defineProperty(exports, Symbol.toStringTag, { value: 'Module' });
const require_runtime = require('../_virtual/_rolldown/runtime.cjs');
let __pg_core_index_ts = require("../pg-core/index.cjs");

//#region src/neon/neon-auth.ts
const neonAuthSchema = (0, __pg_core_index_ts.pgSchema)("neon_auth");
/**
* Table schema of the `users_sync` table used by Neon Auth.
* This table automatically synchronizes and stores user data from external authentication providers.
*
* @schema neon_auth
* @table users_sync
*/
const usersSync = neonAuthSchema.table("users_sync", {
	rawJson: (0, __pg_core_index_ts.jsonb)("raw_json").notNull(),
	id: (0, __pg_core_index_ts.text)().primaryKey().notNull(),
	name: (0, __pg_core_index_ts.text)(),
	email: (0, __pg_core_index_ts.text)(),
	createdAt: (0, __pg_core_index_ts.timestamp)("created_at", {
		withTimezone: true,
		mode: "string"
	}),
	deletedAt: (0, __pg_core_index_ts.timestamp)("deleted_at", {
		withTimezone: true,
		mode: "string"
	}),
	updatedAt: (0, __pg_core_index_ts.timestamp)("updated_at", {
		withTimezone: true,
		mode: "string"
	})
});

//#endregion
exports.usersSync = usersSync;
//# sourceMappingURL=neon-auth.cjs.map