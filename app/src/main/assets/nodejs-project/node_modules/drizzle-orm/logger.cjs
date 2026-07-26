Object.defineProperty(exports, Symbol.toStringTag, { value: 'Module' });
const require_runtime = require('./_virtual/_rolldown/runtime.cjs');
let __entity_ts = require("./entity.cjs");

//#region src/logger.ts
var ConsoleLogWriter = class {
	static [__entity_ts.entityKind] = "ConsoleLogWriter";
	write(message) {
		console.log(message);
	}
};
var DefaultLogger = class {
	static [__entity_ts.entityKind] = "DefaultLogger";
	writer;
	constructor(config) {
		this.writer = config?.writer ?? new ConsoleLogWriter();
	}
	logQuery(query, params) {
		const stringifiedParams = params.map((p) => {
			try {
				return JSON.stringify(p);
			} catch {
				return String(p);
			}
		});
		const paramsStr = stringifiedParams.length ? ` -- params: [${stringifiedParams.join(", ")}]` : "";
		this.writer.write(`Query: ${query}${paramsStr}`);
	}
};
var NoopLogger = class {
	static [__entity_ts.entityKind] = "NoopLogger";
	logQuery() {}
};

//#endregion
exports.ConsoleLogWriter = ConsoleLogWriter;
exports.DefaultLogger = DefaultLogger;
exports.NoopLogger = NoopLogger;
//# sourceMappingURL=logger.cjs.map