import * as Effectable from "effect/Effectable";

//#region src/effect-core/query-effect.ts
function applyEffectWrapper(baseClass) {
	Object.assign(baseClass.prototype, Effectable.Prototype({
		label: "DrizzleQuery",
		evaluate() {
			return this.execute();
		}
	}));
}

//#endregion
export { applyEffectWrapper };
//# sourceMappingURL=query-effect.js.map