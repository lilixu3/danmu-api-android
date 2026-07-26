Object.defineProperty(exports, Symbol.toStringTag, { value: 'Module' });
const require_runtime = require('../_virtual/_rolldown/runtime.cjs');
const require_up_migrations_utils = require('./utils.cjs');
let __sql_sql_ts = require("../sql/sql.cjs");

//#region src/up-migrations/mssql.ts
/**
* Map of upgrade functions. Each key is the version being upgraded FROM,
* and the function upgrades the table to the next version.
*/
const upgradeFunctions = { 0: async (migrationsSchema, migrationsTable, session, localMigrations) => {
	const table = __sql_sql_ts.sql`${__sql_sql_ts.sql.identifier(migrationsSchema)}.${__sql_sql_ts.sql.identifier(migrationsTable)}`;
	const dbRows = await session.execute(__sql_sql_ts.sql`SELECT id, hash, created_at FROM ${table} ORDER BY id ASC`);
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
	for (const dbRow of dbRows.recordset) {
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
	await session.transaction(async (tx) => {
		await tx.execute(__sql_sql_ts.sql`ALTER TABLE ${table} ADD ${__sql_sql_ts.sql.identifier("name")} text`);
		await tx.execute(__sql_sql_ts.sql`ALTER TABLE ${table} ADD ${__sql_sql_ts.sql.identifier("applied_at")} datetime2 DEFAULT GETUTCDATE()`);
		for (const backfillEntry of toApply) await tx.execute(__sql_sql_ts.sql`UPDATE ${table} SET ${__sql_sql_ts.sql.identifier("name")} = ${backfillEntry.name}, ${__sql_sql_ts.sql.identifier("applied_at")} = NULL WHERE ${__sql_sql_ts.sql.identifier("id")} = ${backfillEntry.id}`);
	});
} };
/**
* Detects the current version of the migrations table schema and upgrades it if needed.
*
* Version 0: Original schema (id, hash, created_at)
* Version 1: Extended schema (id, hash, created_at, name, applied_at)
*/
async function upgradeIfNeeded(migrationsSchema, migrationsTable, session, localMigrations) {
	if ((await session.execute(__sql_sql_ts.sql`SELECT 1 FROM INFORMATION_SCHEMA.TABLES 
			WHERE TABLE_SCHEMA = ${migrationsSchema} 
			AND TABLE_NAME = ${migrationsTable}`)).recordset.length === 0) return { newDb: true };
	const rows = await session.execute(__sql_sql_ts.sql`SELECT COLUMN_NAME as [column_name]
		FROM INFORMATION_SCHEMA.COLUMNS
		WHERE TABLE_SCHEMA = ${migrationsSchema}
		AND TABLE_NAME = ${migrationsTable}
		ORDER BY ORDINAL_POSITION`);
	const version = require_up_migrations_utils.GET_VERSION_FOR.mssql(rows.recordset.map((r) => r.column_name));
	for (let v = version; v < require_up_migrations_utils.MIGRATIONS_TABLE_VERSIONS.mssql; v++) {
		const upgradeFn = upgradeFunctions[v];
		if (!upgradeFn) throw new Error(`No upgrade path from migration table version ${v} to ${v + 1}`);
		await upgradeFn(migrationsSchema, migrationsTable, session, localMigrations);
	}
	return { newDb: false };
}

//#endregion
exports.upgradeIfNeeded = upgradeIfNeeded;
//# sourceMappingURL=mssql.cjs.map