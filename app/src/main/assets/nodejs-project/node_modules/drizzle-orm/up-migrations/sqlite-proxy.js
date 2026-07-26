import { GET_VERSION_FOR, MIGRATIONS_TABLE_VERSIONS } from "./utils.js";
import { sql } from "../sql/sql.js";

//#region src/up-migrations/sqlite-proxy.ts
/**
* Detects the current version of the migrations table schema and upgrades it if needed.
*
* Version 0: Original schema (id, hash, created_at)
* Version 1: Extended schema (id, hash, created_at, name, applied_at)
*/
async function upgradeAsyncIfNeeded(migrationsTable, db, callback, localMigrations) {
	if ((await db.session.arrays(sql`SELECT 1 FROM sqlite_master WHERE type = 'table' AND name = ${migrationsTable}`)).length === 0) return { newDb: true };
	const rows = await db.session.arrays(sql`SELECT name as column_name FROM pragma_table_info(${migrationsTable})`);
	const version = GET_VERSION_FOR.sqlite(rows.map((r) => r[0]));
	for (let v = version; v < MIGRATIONS_TABLE_VERSIONS.sqlite; v++) {
		const upgradeFn = upgradeAsyncFunctions[v];
		if (!upgradeFn) throw new Error(`No upgrade path from migration table version ${v} to ${v + 1}`);
		await upgradeFn(migrationsTable, db, callback, localMigrations);
	}
	return { newDb: false };
}
const upgradeAsyncFunctions = { 0: async (migrationsTable, db, callback, localMigrations) => {
	const table = sql`${sql.identifier(migrationsTable)}`;
	const dbRows = (await db.session.arrays(sql`SELECT id, hash, created_at FROM ${table} ORDER BY id ASC`)).map((row) => ({
		id: row[0],
		hash: row[1],
		created_at: row[2]
	}));
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
	const statements = [db.dialect.sqlToQuery(sql`ALTER TABLE ${table} ADD COLUMN ${sql.identifier("name")} text`.inlineParams()).sql, db.dialect.sqlToQuery(sql`ALTER TABLE ${table} ADD COLUMN ${sql.identifier("applied_at")} TEXT`.inlineParams()).sql];
	for (const backfillEntry of toApply) {
		const updateQuery = sql`UPDATE ${table} SET ${sql.identifier("name")} = ${backfillEntry.name}, ${sql.identifier("applied_at")} = NULL WHERE`;
		if (backfillEntry.id) updateQuery.append(sql` ${sql.identifier("id")} = ${backfillEntry.id}`);
		else if (backfillEntry.matchedBy === "millis") updateQuery.append(sql` ${sql.identifier("created_at")} = ${backfillEntry.created_at}`);
		else updateQuery.append(sql` ${sql.identifier("hash")} = ${backfillEntry.hash}`);
		statements.push(db.dialect.sqlToQuery(updateQuery.inlineParams()).sql);
	}
	await callback(statements);
} };

//#endregion
export { upgradeAsyncIfNeeded };
//# sourceMappingURL=sqlite-proxy.js.map