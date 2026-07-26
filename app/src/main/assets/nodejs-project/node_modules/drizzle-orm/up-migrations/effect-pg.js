import { GET_VERSION_FOR, MIGRATIONS_TABLE_VERSIONS } from "./utils.js";
import { sql } from "../sql/sql.js";
import { Effect } from "effect";

//#region src/up-migrations/effect-pg.ts
/**
* Map of upgrade functions. Each key is the version being upgraded FROM,
* and the function upgrades the table to the next version.
*/
const upgradeFunctions = { 0: (migrationsSchema, migrationsTable, session, localMigrations) => {
	return Effect.gen(function* () {
		const table = sql`${sql.identifier(migrationsSchema)}.${sql.identifier(migrationsTable)}`;
		const dbRows = yield* session.objects(sql`SELECT id, hash, created_at FROM ${table} ORDER BY id ASC`);
		localMigrations.sort((a, b) => a.folderMillis !== b.folderMillis ? a.folderMillis - b.folderMillis : (a.name ?? "").localeCompare(b.name ?? ""));
		const byMillis = /* @__PURE__ */ new Map();
		const byHash = /* @__PURE__ */ new Map();
		for (const lm of localMigrations) {
			if (!byMillis.has(lm.folderMillis)) byMillis.set(lm.folderMillis, []);
			byMillis.get(lm.folderMillis).push(lm);
			byHash.set(lm.hash, lm);
		}
		const toApply = [];
		let unmatchedIds = [];
		for (const dbRow of dbRows) {
			const stringified = String(dbRow.created_at);
			const millis = Number(stringified.substring(0, stringified.length - 3) + "000");
			const candidates = byMillis.get(millis);
			let matched;
			if (candidates && candidates.length === 1) matched = candidates[0];
			else if (candidates && candidates.length > 1) matched = candidates.find((c) => c.hash === dbRow.hash);
			else matched = byHash.get(dbRow.hash);
			if (matched) toApply.push({
				id: dbRow.id,
				name: matched.name
			});
			else unmatchedIds.push(dbRow.id);
		}
		if (unmatchedIds.length > 0) throw Error(`While upgrading your database migrations table we found ${unmatchedIds.length} migrations (ids: ${unmatchedIds.join(", ")}) in the database that do not match any local migration. This means that some migrations were applied to the database but are missing from the local environment`);
		yield* session.transaction((tx) => Effect.gen(function* () {
			yield* tx.execute(sql`ALTER TABLE ${table} ADD COLUMN IF NOT EXISTS ${sql.identifier("name")} text`);
			yield* tx.execute(sql`ALTER TABLE ${table} ADD COLUMN IF NOT EXISTS ${sql.identifier("applied_at")} timestamp with time zone DEFAULT now()`);
			for (const backfillEntry of toApply) yield* tx.execute(sql`UPDATE ${table} SET ${sql.identifier("name")} = ${backfillEntry.name}, ${sql.identifier("applied_at")} = NULL WHERE ${sql.identifier("id")} = ${backfillEntry.id}`);
		}));
	});
} };
/**
* Detects the current version of the migrations table schema and upgrades it if needed.
*
* Version 0: Original schema (id, hash, created_at)
* Version 1: Extended schema (id, hash, created_at, name, applied_at)
*/
const upgradeIfNeeded = Effect.fn("upgradeIfNeeded")(function* (migrationsSchema, migrationsTable, session, localMigrations) {
	if ((yield* session.objects(sql`SELECT 1 FROM information_schema.tables
			WHERE table_schema = ${migrationsSchema}
			AND table_name = ${migrationsTable}`)).length === 0) return { newDb: true };
	const rows = yield* session.objects(sql`SELECT
			n.nspname AS "schema",
			c.relname AS "table_name",
			a.attname AS "column_name",
			pg_catalog.format_type(a.atttypid, a.atttypmod) AS "type"
		FROM
			pg_catalog.pg_attribute a
			JOIN pg_catalog.pg_class c ON c.oid = a.attrelid
			JOIN pg_catalog.pg_namespace n ON n.oid = c.relnamespace
		WHERE
			a.attnum > 0
			AND NOT a.attisdropped
			AND n.nspname = ${migrationsSchema}
			AND c.relname = ${migrationsTable}
		ORDER BY a.attnum;`);
	const version = GET_VERSION_FOR.effect(rows.map((r) => r.column_name));
	for (let v = version; v < MIGRATIONS_TABLE_VERSIONS.effect; v++) {
		const upgradeFn = upgradeFunctions[v];
		if (!upgradeFn) throw new Error(`No upgrade path from migration table version ${v} to ${v + 1}`);
		yield* upgradeFn(migrationsSchema, migrationsTable, session, localMigrations);
	}
	return { newDb: false };
});

//#endregion
export { upgradeIfNeeded };
//# sourceMappingURL=effect-pg.js.map