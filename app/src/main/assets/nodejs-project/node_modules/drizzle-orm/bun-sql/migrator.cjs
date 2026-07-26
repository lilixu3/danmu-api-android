Object.defineProperty(exports, Symbol.toStringTag, { value: 'Module' });
const require_bun_sql_mysql_migrator = require('./mysql/migrator.cjs');
const require_bun_sql_postgres_migrator = require('./postgres/migrator.cjs');
const require_bun_sql_sqlite_migrator = require('./sqlite/migrator.cjs');

//#region src/bun-sql/migrator.ts
async function migrate(db, config) {
	return require_bun_sql_postgres_migrator.migrate(db, config);
}
(function(_migrate) {
	async function postgres(db, config) {
		return require_bun_sql_postgres_migrator.migrate(db, config);
	}
	_migrate.postgres = postgres;
	async function sqlite(db, config) {
		return require_bun_sql_sqlite_migrator.migrate(db, config);
	}
	_migrate.sqlite = sqlite;
	async function mysql(db, config) {
		return require_bun_sql_mysql_migrator.migrate(db, config);
	}
	_migrate.mysql = mysql;
})(migrate || (migrate = {}));

//#endregion
Object.defineProperty(exports, 'migrate', {
  enumerable: true,
  get: function () {
    return migrate;
  }
});
//# sourceMappingURL=migrator.cjs.map