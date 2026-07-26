Object.defineProperty(exports, Symbol.toStringTag, { value: 'Module' });
const require_runtime = require('../_virtual/_rolldown/runtime.cjs');
const require_up_migrations_utils = require('./utils.cjs');
let __sql_sql_ts = require("../sql/sql.cjs");
let effect = require("effect");

//#region src/up-migrations/effect-sqlite.ts
/**
* Map of upgrade functions. Each key is the version being upgraded FROM,
* and the function upgrades the table to the next version.
*/
const upgradeFunctions = { 0: (migrationsTable, session, localMigrations) => {
	return effect.Effect.gen(function* () {
		const table = __sql_sql_ts.sql`${__sql_sql_ts.sql.identifier(migrationsTable)}`;
		const dbRows = yield* session.objects(__sql_sql_ts.sql`SELECT id, hash, created_at FROM ${table} ORDER BY id ASC`);
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
		yield* session.transaction((tx) => effect.Effect.gen(function* () {
			yield* tx.run(__sql_sql_ts.sql`ALTER TABLE ${table} ADD COLUMN ${__sql_sql_ts.sql.identifier("name")} text`);
			yield* tx.run(__sql_sql_ts.sql`ALTER TABLE ${table} ADD COLUMN ${__sql_sql_ts.sql.identifier("applied_at")} TEXT`);
			for (const backfillEntry of toApply) {
				const updateQuery = __sql_sql_ts.sql`UPDATE ${table} SET ${__sql_sql_ts.sql.identifier("name")} = ${backfillEntry.name}, ${__sql_sql_ts.sql.identifier("applied_at")} = NULL WHERE`;
				if (backfillEntry.id) updateQuery.append(__sql_sql_ts.sql` ${__sql_sql_ts.sql.identifier("id")} = ${backfillEntry.id}`);
				else if (backfillEntry.matchedBy === "millis") updateQuery.append(__sql_sql_ts.sql` ${__sql_sql_ts.sql.identifier("created_at")} = ${backfillEntry.created_at}`);
				else updateQuery.append(__sql_sql_ts.sql` ${__sql_sql_ts.sql.identifier("hash")} = ${backfillEntry.hash}`);
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
const upgradeIfNeeded = effect.Effect.fn("upgradeIfNeeded")(function* (migrationsTable, session, localMigrations) {
	if ((yield* session.objects(__sql_sql_ts.sql`SELECT 1 FROM sqlite_master WHERE type = 'table' AND name = ${migrationsTable}`)).length === 0) return { newDb: true };
	const rows = yield* session.objects(__sql_sql_ts.sql`SELECT name as column_name FROM pragma_table_info(${migrationsTable})`);
	const version = require_up_migrations_utils.GET_VERSION_FOR.sqlite(rows.map((r) => r.column_name));
	for (let v = version; v < require_up_migrations_utils.MIGRATIONS_TABLE_VERSIONS.sqlite; v++) {
		const upgradeFn = upgradeFunctions[v];
		if (!upgradeFn) throw new Error(`No upgrade path from migration table version ${v} to ${v + 1}`);
		yield* upgradeFn(migrationsTable, session, localMigrations);
	}
	return { newDb: false };
});

//#endregion
exports.upgradeIfNeeded = upgradeIfNeeded;
//# sourceMappingURL=effect-sqlite.cjs.map