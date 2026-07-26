import { bigint, int8 } from "./bigint.js";
import { bit } from "./bit.js";
import { bool } from "./bool.js";
import { char } from "./char.js";
import { customType } from "./custom.js";
import { date } from "./date.js";
import { decimal, numeric } from "./decimal.js";
import { doublePrecision, float } from "./float.js";
import { geometry } from "./geometry.js";
import { inet } from "./inet.js";
import { int4 } from "./integer.js";
import { interval } from "./interval.js";
import { jsonb } from "./jsonb.js";
import { real } from "./real.js";
import { int2, smallint } from "./smallint.js";
import { string, text } from "./string.js";
import { time } from "./time.js";
import { timestamp } from "./timestamp.js";
import { uuid } from "./uuid.js";
import { varbit } from "./varbit.js";
import { varchar } from "./varchar.js";
import { vector } from "./vector.js";

//#region src/cockroach-core/columns/all.ts
function getCockroachColumnBuilders() {
	return {
		bigint,
		bool,
		char,
		customType,
		date,
		doublePrecision,
		inet,
		int4,
		int2,
		int8,
		interval,
		jsonb,
		numeric,
		decimal,
		geometry,
		real,
		smallint,
		text,
		time,
		timestamp,
		uuid,
		varchar,
		bit,
		vector,
		float,
		string,
		varbit
	};
}

//#endregion
export { getCockroachColumnBuilders };
//# sourceMappingURL=all.js.map