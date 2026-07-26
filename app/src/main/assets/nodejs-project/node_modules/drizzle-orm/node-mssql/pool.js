import { entityKind } from "../entity.js";
import mssql from "mssql";

//#region src/node-mssql/pool.ts
var AutoPool = class {
	static [entityKind] = "AutoPool";
	pool;
	constructor(config) {
		this.pool = new mssql.ConnectionPool(config);
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
export { AutoPool };
//# sourceMappingURL=pool.js.map