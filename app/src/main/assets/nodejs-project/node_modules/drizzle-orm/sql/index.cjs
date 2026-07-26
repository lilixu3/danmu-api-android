Object.defineProperty(exports, Symbol.toStringTag, { value: 'Module' });
const require_sql_sql = require('./sql.cjs');
const require_sql_expressions_conditions = require('./expressions/conditions.cjs');
const require_sql_expressions_select = require('./expressions/select.cjs');
const require_sql_functions_aggregate = require('./functions/aggregate.cjs');
const require_sql_functions_vector = require('./functions/vector.cjs');

exports.FakePrimitiveParam = require_sql_sql.FakePrimitiveParam;
exports.Name = require_sql_sql.Name;
exports.Param = require_sql_sql.Param;
exports.Placeholder = require_sql_sql.Placeholder;
Object.defineProperty(exports, 'SQL', {
  enumerable: true,
  get: function () {
    return require_sql_sql.SQL;
  }
});
exports.StringChunk = require_sql_sql.StringChunk;
exports.View = require_sql_sql.View;
exports.and = require_sql_expressions_conditions.and;
exports.arrayContained = require_sql_expressions_conditions.arrayContained;
exports.arrayContains = require_sql_expressions_conditions.arrayContains;
exports.arrayOverlaps = require_sql_expressions_conditions.arrayOverlaps;
exports.asc = require_sql_expressions_select.asc;
exports.avg = require_sql_functions_aggregate.avg;
exports.avgDistinct = require_sql_functions_aggregate.avgDistinct;
exports.between = require_sql_expressions_conditions.between;
exports.bindIfParam = require_sql_expressions_conditions.bindIfParam;
exports.cosineDistance = require_sql_functions_vector.cosineDistance;
exports.count = require_sql_functions_aggregate.count;
exports.countDistinct = require_sql_functions_aggregate.countDistinct;
exports.desc = require_sql_expressions_select.desc;
exports.eq = require_sql_expressions_conditions.eq;
exports.exists = require_sql_expressions_conditions.exists;
exports.fillPlaceholders = require_sql_sql.fillPlaceholders;
exports.getViewName = require_sql_sql.getViewName;
exports.gt = require_sql_expressions_conditions.gt;
exports.gte = require_sql_expressions_conditions.gte;
exports.hammingDistance = require_sql_functions_vector.hammingDistance;
exports.ilike = require_sql_expressions_conditions.ilike;
exports.inArray = require_sql_expressions_conditions.inArray;
exports.innerProduct = require_sql_functions_vector.innerProduct;
exports.isDriverValueEncoder = require_sql_sql.isDriverValueEncoder;
exports.isNoop = require_sql_sql.isNoop;
exports.isNotNull = require_sql_expressions_conditions.isNotNull;
exports.isNull = require_sql_expressions_conditions.isNull;
exports.isSQLWrapper = require_sql_sql.isSQLWrapper;
exports.isView = require_sql_sql.isView;
exports.jaccardDistance = require_sql_functions_vector.jaccardDistance;
exports.l1Distance = require_sql_functions_vector.l1Distance;
exports.l2Distance = require_sql_functions_vector.l2Distance;
exports.like = require_sql_expressions_conditions.like;
exports.lt = require_sql_expressions_conditions.lt;
exports.lte = require_sql_expressions_conditions.lte;
exports.max = require_sql_functions_aggregate.max;
exports.min = require_sql_functions_aggregate.min;
exports.name = require_sql_sql.name;
exports.ne = require_sql_expressions_conditions.ne;
exports.noopDecoder = require_sql_sql.noopDecoder;
exports.noopEncoder = require_sql_sql.noopEncoder;
exports.noopMapper = require_sql_sql.noopMapper;
exports.not = require_sql_expressions_conditions.not;
exports.notBetween = require_sql_expressions_conditions.notBetween;
exports.notExists = require_sql_expressions_conditions.notExists;
exports.notIlike = require_sql_expressions_conditions.notIlike;
exports.notInArray = require_sql_expressions_conditions.notInArray;
exports.notLike = require_sql_expressions_conditions.notLike;
exports.or = require_sql_expressions_conditions.or;
exports.param = require_sql_sql.param;
exports.placeholder = require_sql_sql.placeholder;
Object.defineProperty(exports, 'sql', {
  enumerable: true,
  get: function () {
    return require_sql_sql.sql;
  }
});
Object.defineProperty(exports, 'sqlCommenter', {
  enumerable: true,
  get: function () {
    return require_sql_sql.sqlCommenter;
  }
});
exports.sum = require_sql_functions_aggregate.sum;
exports.sumDistinct = require_sql_functions_aggregate.sumDistinct;