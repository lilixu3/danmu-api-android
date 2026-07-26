Object.defineProperty(exports, Symbol.toStringTag, { value: 'Module' });

//#region src/up-migrations/utils.ts
const MIGRATIONS_TABLE_VERSIONS = {
	sqlite: 1,
	pg: 1,
	effect: 1,
	mysql: 1,
	mssql: 1,
	cockroach: 1,
	singlestore: 1
};
const GET_VERSION_FOR = {
	mysql: (columns) => {
		if (columns.includes("name")) return 1;
		return 0;
	},
	pg: (columns) => {
		if (columns.includes("name")) return 1;
		return 0;
	},
	effect: (columns) => {
		if (columns.includes("name")) return 1;
		return 0;
	},
	mssql: (columns) => {
		if (columns.includes("name")) return 1;
		return 0;
	},
	cockroach: (columns) => {
		if (columns.includes("name")) return 1;
		return 0;
	},
	singlestore: (columns) => {
		if (columns.includes("name")) return 1;
		return 0;
	},
	sqlite: (columns) => {
		if (columns.includes("name")) return 1;
		return 0;
	}
};

//#endregion
exports.GET_VERSION_FOR = GET_VERSION_FOR;
exports.MIGRATIONS_TABLE_VERSIONS = MIGRATIONS_TABLE_VERSIONS;
//# sourceMappingURL=utils.cjs.map