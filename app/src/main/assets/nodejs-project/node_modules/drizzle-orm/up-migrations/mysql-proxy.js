import { GET_VERSION_FOR, MIGRATIONS_TABLE_VERSIONS } from "./utils.js";
import { sql } from "../sql/sql.js";

//#region src/up-migrations/mysql-proxy.ts
/**
* Map of upgrade functions. Each key is the version being upgraded FROM,
* and the function upgrades the table to the next version.
*/
const upgradeFunctions = { 0: async (migrationsTable, db, callback, localMigrations) => {
	const table = sql`${sql.identifier(migrationsTable)}`;
	const dbRows = (await db.session.arrays(sql`SELECT id, hash, created_at FROM ${table} ORDER BY id ASC`)).map((it) => ({
		id: it[0],
		hash: it[1],
		created_at: it[2]
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
	const statements = [db.dialect.sqlToQuery(sql`ALTER TABLE ${table} ADD ${sql.identifier("name")} text`.inlineParams()).sql, db.dialect.sqlToQuery(sql`ALTER TABLE ${table} ADD ${sql.identifier("applied_at")} TIMESTAMP DEFAULT CURRENT_TIMESTAMP`.inlineParams()).sql];
	for (const backfillEntry of toApply) statements.push(db.dialect.sqlToQuery(sql`UPDATE ${table} SET ${sql.identifier("name")} = ${backfillEntry.name}, ${sql.identifier("applied_at")} = NULL WHERE ${sql.identifier("id")} = ${backfillEntry.id}`.inlineParams()).sql);
	await callback(statements);
} };
/**
* Detects the current version of the migrations table schema and upgrades it if needed.
*
* Version 0: Original schema (id, hash, created_at)
* Version 1: Extended schema (id, hash, created_at, name, applied_at)
*/
async function upgradeIfNeeded(migrationsTable, db, callback, localMigrations) {
	if ((await db.session.arrays(sql`SELECT 1 FROM information_schema.tables 
			WHERE table_name = ${migrationsTable}
			AND table_schema = DATABASE()`)).length === 0) return { newDb: true };
	const rows = await db.session.arrays(sql`SELECT column_name as \`column_name\`
		FROM information_schema.columns
		WHERE table_name = ${migrationsTable}
		AND table_schema = DATABASE()
		ORDER BY ordinal_position`);
	const version = GET_VERSION_FOR.mysql(rows.map((r) => r[0]));
	for (let v = version; v < MIGRATIONS_TABLE_VERSIONS.mysql; v++) {
		const upgradeFn = upgradeFunctions[v];
		if (!upgradeFn) throw new Error(`No upgrade path from migration table version ${v} to ${v + 1}`);
		await upgradeFn(migrationsTable, db, callback, localMigrations);
	}
	return { newDb: false };
}

//#endregion
export { upgradeIfNeeded };
//# sourceMappingURL=mysql-proxy.js.map