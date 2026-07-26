Object.defineProperty(exports, Symbol.toStringTag, { value: 'Module' });
const require_runtime = require('./_virtual/_rolldown/runtime.cjs');
let __entity_ts = require("./entity.cjs");

//#region src/query-promise.ts
var QueryPromise = class {
	static [__entity_ts.entityKind] = "QueryPromise";
	[Symbol.toStringTag] = "QueryPromise";
	catch(onRejected) {
		return this.then(void 0, onRejected);
	}
	finally(onFinally) {
		return this.then((value) => {
			onFinally?.();
			return value;
		}, (reason) => {
			onFinally?.();
			throw reason;
		});
	}
	then(onFulfilled, onRejected) {
		return this.execute().then(onFulfilled, onRejected);
	}
};

//#endregion
exports.QueryPromise = QueryPromise;
//# sourceMappingURL=query-promise.cjs.map