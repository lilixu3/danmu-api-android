import { bigint } from "./bigint.js";
import { binary } from "./binary.js";
import { bit } from "./bit.js";
import { char } from "./char.js";
import { customType } from "./custom.js";
import { date } from "./date.js";
import { datetime } from "./datetime.js";
import { datetime2 } from "./datetime2.js";
import { datetimeoffset } from "./datetimeoffset.js";
import { decimal } from "./decimal.js";
import { float } from "./float.js";
import { int } from "./int.js";
import { numeric } from "./numeric.js";
import { real } from "./real.js";
import { smallint } from "./smallint.js";
import { ntext, text } from "./text.js";
import { time } from "./time.js";
import { tinyint } from "./tinyint.js";
import { varbinary } from "./varbinary.js";
import { nvarchar, varchar } from "./varchar.js";

//#region src/mssql-core/columns/all.ts
function getMsSqlColumnBuilders() {
	return {
		bigint,
		binary,
		bit,
		char,
		customType,
		date,
		datetime,
		datetime2,
		datetimeoffset,
		decimal,
		float,
		int,
		real,
		numeric,
		smallint,
		text,
		ntext,
		time,
		tinyint,
		varbinary,
		varchar,
		nvarchar
	};
}

//#endregion
export { getMsSqlColumnBuilders };
//# sourceMappingURL=all.js.map