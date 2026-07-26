import { entityKind } from "../entity.js";
import { View } from "../sql/sql.js";

//#region src/cockroach-core/view-base.ts
var CockroachViewBase = class extends View {
	static [entityKind] = "CockroachViewBase";
};

//#endregion
export { CockroachViewBase };
//# sourceMappingURL=view-base.js.map