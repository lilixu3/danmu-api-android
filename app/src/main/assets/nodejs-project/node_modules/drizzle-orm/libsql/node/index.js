import { construct } from "../driver-core.js";
import { createClient } from "@libsql/client/node";

//#region src/libsql/node/index.ts
function drizzle(...params) {
	if (typeof params[0] === "string") return construct(createClient({ url: params[0] }), params[1]);
	const { connection, client, ...DrizzleSQLiteConfig } = params[0];
	if (client) return construct(client, DrizzleSQLiteConfig);
	return construct(typeof connection === "string" ? createClient({ url: connection }) : createClient(connection), DrizzleSQLiteConfig);
}
(function(_drizzle) {
	function mock(config) {
		return construct({}, config);
	}
	_drizzle.mock = mock;
})(drizzle || (drizzle = {}));

//#endregion
export { drizzle };
//# sourceMappingURL=index.js.map