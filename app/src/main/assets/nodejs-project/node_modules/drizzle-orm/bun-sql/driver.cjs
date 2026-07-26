Object.defineProperty(exports, Symbol.toStringTag, { value: 'Module' });
const require_bun_sql_mysql_driver = require('./mysql/driver.cjs');
const require_bun_sql_postgres_driver = require('./postgres/driver.cjs');
const require_bun_sql_sqlite_driver = require('./sqlite/driver.cjs');

//#region src/bun-sql/driver.ts
function drizzle(...params) {
	return require_bun_sql_postgres_driver.drizzle(...params);
}
(function(_drizzle) {
	function mock(config) {
		return require_bun_sql_postgres_driver.drizzle.mock(config);
	}
	_drizzle.mock = mock;
	function postgres(...params) {
		return require_bun_sql_postgres_driver.drizzle(...params);
	}
	_drizzle.postgres = postgres;
	(function(_postgres) {
		function mock(config) {
			return require_bun_sql_postgres_driver.drizzle.mock(config);
		}
		_postgres.mock = mock;
	})(postgres || (postgres = _drizzle.postgres || (_drizzle.postgres = {})));
	function sqlite(...params) {
		return require_bun_sql_sqlite_driver.drizzle(...params);
	}
	_drizzle.sqlite = sqlite;
	(function(_sqlite) {
		function mock(config) {
			return require_bun_sql_sqlite_driver.drizzle.mock(config);
		}
		_sqlite.mock = mock;
	})(sqlite || (sqlite = _drizzle.sqlite || (_drizzle.sqlite = {})));
	function mysql(...params) {
		return require_bun_sql_mysql_driver.drizzle(...params);
	}
	_drizzle.mysql = mysql;
	(function(_mysql) {
		function mock(config) {
			return require_bun_sql_mysql_driver.drizzle.mock(config);
		}
		_mysql.mock = mock;
	})(mysql || (mysql = _drizzle.mysql || (_drizzle.mysql = {})));
})(drizzle || (drizzle = {}));

//#endregion
Object.defineProperty(exports, 'drizzle', {
  enumerable: true,
  get: function () {
    return drizzle;
  }
});
//# sourceMappingURL=driver.cjs.map