import { construct } from "./driver-core.js";
import { Database } from "@tursodatabase/database-wasm";

//#region src/tursodatabase/wasm.ts
function drizzle(...params) {
	if (typeof params[0] === "string") return construct(new Database(params[0]), params[1]);
	const { connection, client, ...DrizzleSQLiteConfig } = params[0];
	if (client) return construct(client, DrizzleSQLiteConfig);
	return construct(typeof connection === "string" ? new Database(connection) : new Database(connection.path, connection), DrizzleSQLiteConfig);
}
(function(_drizzle) {
	function mock(config) {
		return construct({}, config);
	}
	_drizzle.mock = mock;
})(drizzle || (drizzle = {}));

//#endregion
export { drizzle };
//# sourceMappingURL=wasm.js.map