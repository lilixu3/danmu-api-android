import { EffectLogger } from "./logger.js";
import { EffectCache } from "../cache/core/cache-effect.js";
import { Layer } from "effect";

//#region src/effect-core/defaults.ts
const DefaultServices = Layer.merge(EffectCache.Default, EffectLogger.Default);

//#endregion
export { DefaultServices };
//# sourceMappingURL=defaults.js.map