Object.defineProperty(exports, Symbol.toStringTag, { value: 'Module' });
const require_neon_neon_auth = require('./neon-auth.cjs');
const require_neon_rls = require('./rls.cjs');

exports.anonymousRole = require_neon_rls.anonymousRole;
exports.authUid = require_neon_rls.authUid;
exports.authenticatedRole = require_neon_rls.authenticatedRole;
exports.crudPolicy = require_neon_rls.crudPolicy;
exports.usersSync = require_neon_neon_auth.usersSync;