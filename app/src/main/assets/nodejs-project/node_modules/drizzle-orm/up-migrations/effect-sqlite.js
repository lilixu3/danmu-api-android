import { GET_VERSION_FOR, MIGRATIONS_TABLE_VERSIONS } from "./utils.js";
import { sql } from "../sql/sql.js";
import { Effect } from "effect";

//#region src/up-migrations/effect-sqlite.ts
/**
* Map of upgrade functions. Each key is the version being upgraded FROM,
* and the function upgrades the table to the next version.
*/
const upgradeFunctions = { 0: (migrationsTable, session, localMigrations) => {
	return Effect.gen(function* () {
		const table = sql`${sql.identifier(migrationsTable)}`;
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
		let unmatched = [];
		for (const dbRow of dbRows) {
			const stringified = String(dbRow.created_at);
			const millis = Number(stringified.substring(0, stringified.length - 3) + "000");
			const candidates = byMillis.get(millis);
			let matched;
			let matchedBy = null;
			if (candidates && candidates.length === 1) {
				matched = candidates[0];
				matchedBy = "millis";
			} else if (candidates && candidates.length > 1) {
				matched = candidates.find((c) => c.hash && dbRow.hash && c.hash === dbRow.hash);
				if (matched) matchedBy = "hash";
			} else {
				matched = byHash.get(dbRow.hash);
				if (matched) matchedBy = "hash";
			}
			if (matched) toApply.push({
				id: dbRow.id,
				name: matched.name,
				hash: dbRow.hash,
				created_at: stringified,
				matchedBy: dbRow.id ? "id" : matchedBy
			});
			else unmatched.push(dbRow);
		}
		if (unmatched.length > 0) throw Error(`While upgrading your database migrations table we found ${unmatched.length} (${unmatched.map((it) => `[id: ${it.id}, created_at: ${it.created_at}]`).join(", ")}) migrations in the database that do not match any local migration. This means that some migrations were applied to the database but are missing from the local environment`);
		yield* session.transaction((tx) => Effect.gen(function* () {
			yield* tx.run(sql`ALTER TABLE ${table} ADD COLUMN ${sql.identifier("name")} text`);
			yield* tx.run(sql`ALTER TABLE ${table} ADD COLUMN ${sql.identifier("applied_at")} TEXT`);
			for (const backfillEntry of toApply) {
				const updateQuery = sql`UPDATE ${table} SET ${sql.identifier("name")} = ${backfillEntry.name}, ${sql.identifier("applied_at")} = NULL WHERE`;
				if (backfillEntry.id) updateQuery.append(sql` ${sql.identifier("id")} = ${backfillEntry.id}`);
				else if (backfillEntry.matchedBy === "millis") updateQuery.append(sql` ${sql.identifier("created_at")} = ${backfillEntry.created_at}`);
				else updateQuery.append(sql` ${sql.identifier("hash")} = ${backfillEntry.hash}`);
				yield* tx.run(updateQuery);
			}
		}));
	});
} };
/**
* Detects the current version of the migrations table schema and upgrades it if needed.
*
* Version 0: Original schema (id, hash, created_at)
* Version 1: Extended schema (id, hash, created_at, name, applied_at)
*/
const upgradeIfNeeded = Effect.fn("upgradeIfNeeded")(function* (migrationsTable, session, localMigrations) {
	if ((yield* session.objects(sql`SELECT 1 FROM sqlite_master WHERE type = 'table' AND name = ${migrationsTable}`)).length === 0) return { newDb: true };
	const rows = yield* session.objects(sql`SELECT name as column_name FROM pragma_table_info(${migrationsTable})`);
	const version = GET_VERSION_FOR.sqlite(rows.map((r) => r.column_name));
	for (let v = version; v < MIGRATIONS_TABLE_VERSIONS.sqlite; v++) {
		const upgradeFn = upgradeFunctions[v];
		if (!upgradeFn) throw new Error(`No upgrade path from migration table version ${v} to ${v + 1}`);
		yield* upgradeFn(migrationsTable, session, localMigrations);
	}
	return { newDb: false };
});

//#endregion
export { upgradeIfNeeded };
//# sourceMappingURL=effect-sqlite.js.map