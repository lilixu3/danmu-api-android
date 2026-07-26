Object.defineProperty(exports, Symbol.toStringTag, { value: 'Module' });
const require_runtime = require('../_virtual/_rolldown/runtime.cjs');
let __entity_ts = require("../entity.cjs");
let mssql = require("mssql");
mssql = require_runtime.__toESM(mssql);

//#region src/node-mssql/pool.ts
var AutoPool = class {
	static [__entity_ts.entityKind] = "AutoPool";
	pool;
	constructor(config) {
		this.pool = new mssql.default.ConnectionPool(config);
	}
	async $instance() {
		await this.pool.connect().catch((err) => {
			console.error("❌ AutoPool failed to connect:", err);
			throw err;
		});
		return this.pool;
	}
};

//#endregion
exports.AutoPool = AutoPool;
//# sourceMappingURL=pool.cjs.map