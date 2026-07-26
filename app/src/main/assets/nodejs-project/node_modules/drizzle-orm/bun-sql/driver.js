import { drizzle as drizzle$1 } from "./mysql/driver.js";
import { drizzle as drizzle$2 } from "./postgres/driver.js";
import { drizzle as drizzle$3 } from "./sqlite/driver.js";

//#region src/bun-sql/driver.ts
function drizzle(...params) {
	return drizzle$2(...params);
}
(function(_drizzle) {
	function mock(config) {
		return drizzle$2.mock(config);
	}
	_drizzle.mock = mock;
	function postgres(...params) {
		return drizzle$2(...params);
	}
	_drizzle.postgres = postgres;
	(function(_postgres) {
		function mock(config) {
			return drizzle$2.mock(config);
		}
		_postgres.mock = mock;
	})(postgres || (postgres = _drizzle.postgres || (_drizzle.postgres = {})));
	function sqlite(...params) {
		return drizzle$3(...params);
	}
	_drizzle.sqlite = sqlite;
	(function(_sqlite) {
		function mock(config) {
			return drizzle$3.mock(config);
		}
		_sqlite.mock = mock;
	})(sqlite || (sqlite = _drizzle.sqlite || (_drizzle.sqlite = {})));
	function mysql(...params) {
		return drizzle$1(...params);
	}
	_drizzle.mysql = mysql;
	(function(_mysql) {
		function mock(config) {
			return drizzle$1.mock(config);
		}
		_mysql.mock = mock;
	})(mysql || (mysql = _drizzle.mysql || (_drizzle.mysql = {})));
})(drizzle || (drizzle = {}));

//#endregion
export { drizzle };
//# sourceMappingURL=driver.js.map