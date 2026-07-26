Object.defineProperty(exports, Symbol.toStringTag, { value: 'Module' });
const require_runtime = require('../_virtual/_rolldown/runtime.cjs');
let __entity_ts = require("../entity.cjs");
let effect_Schema = require("effect/Schema");
effect_Schema = require_runtime.__toESM(effect_Schema);

//#region src/effect-core/errors.ts
var EffectDrizzleError = class extends effect_Schema.TaggedErrorClass()("EffectDrizzleError", {
	message: effect_Schema.String,
	cause: effect_Schema.Unknown
}) {
	static [__entity_ts.entityKind] = "EffectDrizzleError";
};
var EffectDrizzleQueryError = class EffectDrizzleQueryError extends effect_Schema.TaggedErrorClass()("EffectDrizzleQueryError", {
	query: effect_Schema.String,
	params: effect_Schema.Array(effect_Schema.Any).pipe(effect_Schema.mutable),
	cause: effect_Schema.Unknown
}) {
	static [__entity_ts.entityKind] = "EffectDrizzleQueryError";
	get message() {
		return `Failed query: ${this.query}\nparams: ${this.params}`;
	}
	constructor(params) {
		super(params);
		Error.captureStackTrace(this, EffectDrizzleQueryError);
	}
};
var EffectTransactionRollbackError = class extends effect_Schema.TaggedErrorClass()("EffectTransactionRollbackError", {}) {
	static [__entity_ts.entityKind] = "EffectTransactionRollbackError";
	message = "Rollback";
};
var MigratorInitError = class extends effect_Schema.TaggedErrorClass()("MigratorInitError", { exitCode: effect_Schema.Literals(["databaseMigrations", "localMigrations"]) }) {
	static [__entity_ts.entityKind] = "MigratorInitError";
};

//#endregion
exports.EffectDrizzleError = EffectDrizzleError;
exports.EffectDrizzleQueryError = EffectDrizzleQueryError;
exports.EffectTransactionRollbackError = EffectTransactionRollbackError;
exports.MigratorInitError = MigratorInitError;
//# sourceMappingURL=errors.cjs.map