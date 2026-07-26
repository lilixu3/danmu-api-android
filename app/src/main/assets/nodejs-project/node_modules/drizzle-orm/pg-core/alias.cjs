Object.defineProperty(exports, Symbol.toStringTag, { value: 'Module' });
const require_runtime = require('../_virtual/_rolldown/runtime.cjs');
let __alias_ts = require("../alias.cjs");

//#region src/pg-core/alias.ts
function alias(table, alias) {
	return new Proxy(table, new __alias_ts.TableAliasProxyHandler(alias, false));
}

//#endregion
exports.alias = alias;
//# sourceMappingURL=alias.cjs.map