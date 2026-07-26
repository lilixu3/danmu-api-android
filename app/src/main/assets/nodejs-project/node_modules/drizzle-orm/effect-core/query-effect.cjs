Object.defineProperty(exports, Symbol.toStringTag, { value: 'Module' });
const require_runtime = require('../_virtual/_rolldown/runtime.cjs');
let effect_Effectable = require("effect/Effectable");
effect_Effectable = require_runtime.__toESM(effect_Effectable);

//#region src/effect-core/query-effect.ts
function applyEffectWrapper(baseClass) {
	Object.assign(baseClass.prototype, effect_Effectable.Prototype({
		label: "DrizzleQuery",
		evaluate() {
			return this.execute();
		}
	}));
}

//#endregion
exports.applyEffectWrapper = applyEffectWrapper;
//# sourceMappingURL=query-effect.cjs.map