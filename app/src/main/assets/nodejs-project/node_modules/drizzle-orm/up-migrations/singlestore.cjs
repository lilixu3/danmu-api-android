Object.defineProperty(exports, Symbol.toStringTag, { value: 'Module' });
const require_runtime = require('../_virtual/_rolldown/runtime.cjs');
const require_up_migrations_utils = require('./utils.cjs');
let __sql_sql_ts = require("../sql/sql.cjs");

//#region src/up-migrations/singlestore.ts
/**
* Map of upgrade functions. Each key is the version being upgraded FROM,
* and the function upgrades the table to the next version.
*/
const upgradeFunctions = { 0: async (migrationsTable, session, localMigrations) => {
	const table = __sql_sql_ts.sql`${__sql_sql_ts.sql.identifier(migrationsTable)}`;
	const dbRows = await session.all(__sql_sql_ts.sql`SELECT id, hash, created_at FROM ${table} ORDER BY id ASC`);
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
	await session.execute(__sql_sql_ts.sql`ALTER TABLE ${table} ADD ${__sql_sql_ts.sql.identifier("name")} text`);
	await session.execute(__sql_sql_ts.sql`ALTER TABLE ${table} ADD ${__sql_sql_ts.sql.identifier("applied_at")} TIMESTAMP DEFAULT CURRENT_TIMESTAMP`);
	for (const backfillEntry of toApply) await session.execute(__sql_sql_ts.sql`UPDATE ${table} SET ${__sql_sql_ts.sql.identifier("name")} = ${backfillEntry.name}, ${__sql_sql_ts.sql.identifier("applied_at")} = NULL WHERE ${__sql_sql_ts.sql.identifier("id")} = ${backfillEntry.id}`);
} };
/**
* Detects the current version of the migrations table schema and upgrades it if needed.
*
* Version 0: Original schema (id, hash, created_at)
* Version 1: Extended schema (id, hash, created_at, name, applied_at)
*/
async function upgradeIfNeeded(migrationsTable, session, localMigrations) {
	if ((await session.all(__sql_sql_ts.sql`SELECT 1 FROM information_schema.tables 
			WHERE table_name = ${migrationsTable}
			AND table_schema = DATABASE()`)).length === 0) return { newDb: true };
	const rows = await session.all(__sql_sql_ts.sql`SELECT column_name as \`column_name\`
		FROM information_schema.columns
		WHERE table_name = ${migrationsTable}
		AND table_schema = DATABASE()
		ORDER BY ordinal_position`);
	const version = require_up_migrations_utils.GET_VERSION_FOR.singlestore(rows.map((r) => r.column_name));
	for (let v = version; v < require_up_migrations_utils.MIGRATIONS_TABLE_VERSIONS.singlestore; v++) {
		const upgradeFn = upgradeFunctions[v];
		if (!upgradeFn) throw new Error(`No upgrade path from migration table version ${v} to ${v + 1}`);
		await upgradeFn(migrationsTable, session, localMigrations);
	}
	return { newDb: false };
}

//#endregion
exports.upgradeIfNeeded = upgradeIfNeeded;
//# sourceMappingURL=singlestore.cjs.map