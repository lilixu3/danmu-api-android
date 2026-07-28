"use strict";
Object.defineProperty(exports, "__esModule", { value: true });
exports.FT_HYBRID_COMBINE_METHOD = exports.FT_HYBRID_VECTOR_METHOD = void 0;
const generic_transformers_1 = require("@redis/client/dist/lib/commands/generic-transformers");
const SEARCH_1 = require("./SEARCH");
const AGGREGATE_1 = require("./AGGREGATE");
/**
 * Vector search method configuration - either KNN or RANGE.
 */
exports.FT_HYBRID_VECTOR_METHOD = {
    /** K-Nearest Neighbors search configuration */
    KNN: "KNN",
    /** Range-based vector search configuration */
    RANGE: "RANGE",
};
/**
 * Score fusion method type constants for combining search results.
 */
exports.FT_HYBRID_COMBINE_METHOD = {
    /** Reciprocal Rank Fusion */
    RRF: "RRF",
    /** Linear combination with ALPHA and BETA weights */
    LINEAR: "LINEAR",
};
function parseSearchExpression(parser, search) {
    parser.push("SEARCH", search.query);
    if (search.SCORER) {
        parser.push("SCORER", search.SCORER);
    }
    if (search.YIELD_SCORE_AS) {
        parser.push("YIELD_SCORE_AS", search.YIELD_SCORE_AS);
    }
}
function parseVectorExpression(parser, vsim) {
    parser.push("VSIM", vsim.field, vsim.vector);
    if (vsim.method) {
        if (vsim.method.type === exports.FT_HYBRID_VECTOR_METHOD.KNN) {
            let argsCount = 2;
            if (vsim.method.EF_RUNTIME !== undefined) {
                argsCount += 2;
            }
            parser.push("KNN", argsCount.toString(), "K", vsim.method.K.toString());
            if (vsim.method.EF_RUNTIME !== undefined) {
                parser.push("EF_RUNTIME", vsim.method.EF_RUNTIME.toString());
            }
        }
        if (vsim.method.type === exports.FT_HYBRID_VECTOR_METHOD.RANGE) {
            let argsCount = 2;
            if (vsim.method.EPSILON !== undefined) {
                argsCount += 2;
            }
            parser.push("RANGE", argsCount.toString(), "RADIUS", vsim.method.RADIUS.toString());
            if (vsim.method.EPSILON !== undefined) {
                parser.push("EPSILON", vsim.method.EPSILON.toString());
            }
        }
    }
    if (vsim.FILTER) {
        parser.push("FILTER", vsim.FILTER);
    }
    if (vsim.YIELD_SCORE_AS) {
        parser.push("YIELD_SCORE_AS", vsim.YIELD_SCORE_AS);
    }
}
function parseCombineMethod(parser, combine) {
    if (!combine)
        return;
    parser.push("COMBINE");
    if (combine.method.type === exports.FT_HYBRID_COMBINE_METHOD.RRF) {
        // Calculate argsCount: 2 per optional (WINDOW, CONSTANT, YIELD_SCORE_AS)
        let argsCount = 0;
        if (combine.method.WINDOW !== undefined) {
            argsCount += 2;
        }
        if (combine.method.CONSTANT !== undefined) {
            argsCount += 2;
        }
        if (combine.YIELD_SCORE_AS) {
            argsCount += 2;
        }
        parser.push("RRF", argsCount.toString());
        if (combine.method.WINDOW !== undefined) {
            parser.push("WINDOW", combine.method.WINDOW.toString());
        }
        if (combine.method.CONSTANT !== undefined) {
            parser.push("CONSTANT", combine.method.CONSTANT.toString());
        }
    }
    if (combine.method.type === exports.FT_HYBRID_COMBINE_METHOD.LINEAR) {
        // Calculate argsCount: 2 per optional (ALPHA, BETA, WINDOW, YIELD_SCORE_AS)
        let argsCount = 0;
        if (combine.method.ALPHA !== undefined) {
            argsCount += 2;
        }
        if (combine.method.BETA !== undefined) {
            argsCount += 2;
        }
        if (combine.method.WINDOW !== undefined) {
            argsCount += 2;
        }
        if (combine.YIELD_SCORE_AS) {
            argsCount += 2;
        }
        parser.push("LINEAR", argsCount.toString());
        if (combine.method.ALPHA !== undefined) {
            parser.push("ALPHA", combine.method.ALPHA.toString());
        }
        if (combine.method.BETA !== undefined) {
            parser.push("BETA", combine.method.BETA.toString());
        }
        if (combine.method.WINDOW !== undefined) {
            parser.push("WINDOW", combine.method.WINDOW.toString());
        }
    }
    if (combine.YIELD_SCORE_AS) {
        parser.push("YIELD_SCORE_AS", combine.YIELD_SCORE_AS);
    }
}
function parseApply(parser, apply) {
    parser.push("APPLY", apply.expression);
    if (apply.AS) {
        parser.push("AS", apply.AS);
    }
}
function parseHybridOptions(parser, options) {
    parseSearchExpression(parser, options.SEARCH);
    parseVectorExpression(parser, options.VSIM);
    if (options.COMBINE) {
        parseCombineMethod(parser, options.COMBINE);
    }
    if (options.LOAD) {
        if (options.LOAD === "*") {
            parser.push("LOAD", "*");
        }
        else {
            (0, generic_transformers_1.parseOptionalVariadicArgument)(parser, "LOAD", options.LOAD);
        }
    }
    if (options.GROUPBY) {
        (0, generic_transformers_1.parseOptionalVariadicArgument)(parser, "GROUPBY", options.GROUPBY.fields);
        if (options.GROUPBY.REDUCE) {
            const reducers = Array.isArray(options.GROUPBY.REDUCE)
                ? options.GROUPBY.REDUCE
                : [options.GROUPBY.REDUCE];
            for (const reducer of reducers) {
                (0, AGGREGATE_1.parseGroupByReducer)(parser, reducer);
            }
        }
    }
    if (options.APPLY) {
        const applies = Array.isArray(options.APPLY)
            ? options.APPLY
            : [options.APPLY];
        for (const apply of applies) {
            parseApply(parser, apply);
        }
    }
    if (options.SORTBY) {
        const sortByArgsCount = options.SORTBY.fields.reduce((acc, field) => {
            if (field.direction) {
                return acc + 2;
            }
            return acc + 1;
        }, 0);
        parser.push("SORTBY", sortByArgsCount.toString());
        for (const sortField of options.SORTBY.fields) {
            parser.push(sortField.field);
            if (sortField.direction) {
                parser.push(sortField.direction);
            }
        }
    }
    if (options.NOSORT) {
        parser.push("NOSORT");
    }
    if (options.FILTER) {
        parser.push("FILTER", options.FILTER);
    }
    if (options.LIMIT) {
        parser.push("LIMIT", options.LIMIT.offset.toString(), options.LIMIT.count.toString());
    }
    const hasParams = options.PARAMS && Object.keys(options.PARAMS).length > 0;
    (0, SEARCH_1.parseParamsArgument)(parser, hasParams ? options.PARAMS : undefined);
    if (options.TIMEOUT !== undefined) {
        parser.push("TIMEOUT", options.TIMEOUT.toString());
    }
}
exports.default = {
    NOT_KEYED_COMMAND: true,
    IS_READ_ONLY: true,
    /**
     * Performs a hybrid search combining multiple search expressions.
     * Supports multiple SEARCH and VECTOR expressions with various fusion methods.
     *
     * @experimental
     * NOTE: FT.Hybrid is still in experimental state
     * It's behaviour and function signature may change
     *
     * @param parser - The command parser
     * @param index - The index name to search
     * @param options - Hybrid search options including:
     *   - SEARCH: Text search expression with optional scoring
     *   - VSIM: Vector similarity expression with KNN/RANGE methods
     *   - COMBINE: Fusion method (RRF, LINEAR)
     *   - Post-processing operations: LOAD, GROUPBY, APPLY, SORTBY, FILTER
     *   - Tunable options: LIMIT, PARAMS, TIMEOUT
     */
    parseCommand(parser, index, options) {
        parser.push("FT.HYBRID", index);
        parseHybridOptions(parser, options);
    },
    transformReply: {
        2: (reply) => {
            return transformHybridSearchResults(reply);
        },
        3: undefined,
    },
    unstableResp3: true,
};
function transformHybridSearchResults(reply) {
    // FT.HYBRID returns a map-like structure as flat array:
    // ['total_results', N, 'results', [...], 'warnings', [...], 'execution_time', 'X.XXX']
    const replyMap = parseReplyMap(reply);
    const totalResults = replyMap["total_results"] ?? 0;
    const rawResults = replyMap["results"] ?? [];
    const warnings = replyMap["warnings"] ?? [];
    const executionTime = replyMap["execution_time"]
        ? Number.parseFloat(replyMap["execution_time"])
        : 0;
    const results = [];
    for (const result of rawResults) {
        // Each result is a flat key-value array like FT.AGGREGATE: ['field1', 'value1', 'field2', 'value2', ...]
        const resultMap = parseReplyMap(result);
        const doc = Object.create(null);
        // Add all other fields from the result
        for (const [key, value] of Object.entries(resultMap)) {
            if (key === "$") {
                // JSON document - parse and merge
                try {
                    Object.assign(doc, JSON.parse(value));
                }
                catch {
                    doc[key] = value;
                }
            }
            else {
                doc[key] = value;
            }
        }
        results.push(doc);
    }
    return {
        totalResults,
        executionTime,
        warnings,
        results,
    };
}
function parseReplyMap(reply) {
    const map = {};
    if (!Array.isArray(reply)) {
        return map;
    }
    for (let i = 0; i < reply.length; i += 2) {
        const key = reply[i];
        const value = reply[i + 1];
        if (typeof key === "string") {
            map[key] = value;
        }
    }
    return map;
}
//# sourceMappingURL=HYBRID.js.map