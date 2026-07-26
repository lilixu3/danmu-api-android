Object.defineProperty(exports, Symbol.toStringTag, { value: 'Module' });
const require_runtime = require('../_virtual/_rolldown/runtime.cjs');
const require_up_migrations_utils = require('./utils.cjs');
let __sql_sql_ts = require("../sql/sql.cjs");

//#region src/up-migrations/cockroach.ts
/**
* Map of upgrade functions. Each key is the version being upgraded FROM,
* and the function upgrades the table to the next version.
*/
const upgradeFunctions = { 0: async (migrationsSchema, migrationsTable, session, localMigrations) => {
	const table = __sql_sql_ts.sql`${__sql_sql_ts.sql.identifier(migrationsSchema)}.${__sql_sql_ts.sql.identifier(migrationsTable)}`;
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
	await session.transaction(async (tx) => {
		await tx.execute(__sql_sql_ts.sql`ALTER TABLE ${table} ADD COLUMN IF NOT EXISTS ${__sql_sql_ts.sql.identifier("name")} text`);
		await tx.execute(__sql_sql_ts.sql`ALTER TABLE ${table} ADD COLUMN IF NOT EXISTS ${__sql_sql_ts.sql.identifier("applied_at")} timestamp with time zone DEFAULT now()`);
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
	if ((await session.all(__sql_sql_ts.sql`SELECT 1 FROM information_schema.tables
			WHERE table_schema = ${migrationsSchema}
			AND table_name = ${migrationsTable}`)).length === 0) return { newDb: true };
	const rows = await session.all(__sql_sql_ts.sql`SELECT
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
	let version = require_up_migrations_utils.GET_VERSION_FOR.cockroach(rows.map((r) => r.column_name));
	for (let v = version; v < require_up_migrations_utils.MIGRATIONS_TABLE_VERSIONS.cockroach; v++) {
		const upgradeFn = upgradeFunctions[v];
		if (!upgradeFn) throw new Error(`No upgrade path from migration table version ${v} to ${v + 1}`);
		await upgradeFn(migrationsSchema, migrationsTable, session, localMigrations);
	}
	return { newDb: false };
}

//#endregion
exports.upgradeIfNeeded = upgradeIfNeeded;
//# sourceMappingURL=cockroach.cjs.map