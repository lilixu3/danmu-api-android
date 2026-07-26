import { TableAliasProxyHandler } from "../alias.js";

//#region src/mssql-core/alias.ts
function alias(table, alias) {
	return new Proxy(table, new TableAliasProxyHandler(alias, false));
}

//#endregion
export { alias };
//# sourceMappingURL=alias.js.map