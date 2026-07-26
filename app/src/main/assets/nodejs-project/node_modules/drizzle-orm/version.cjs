Object.defineProperty(exports, Symbol.toStringTag, { value: 'Module' });
const require_package = require('./package.cjs');

//#region src/version.ts
const compatibilityVersion = 14;

//#endregion
exports.compatibilityVersion = compatibilityVersion;
Object.defineProperty(exports, 'npmVersion', {
  enumerable: true,
  get: function () {
    return require_package.version;
  }
});
//# sourceMappingURL=version.cjs.map