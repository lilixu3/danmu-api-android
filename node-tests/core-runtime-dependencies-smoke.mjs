import assert from 'node:assert/strict';
import { readFileSync } from 'node:fs';
import { pathToFileURL } from 'node:url';
import { dirname, resolve } from 'node:path';

const runtimeRoot = resolve('app/src/main/assets/nodejs-project');
const entrySource = readFileSync(resolve(runtimeRoot, 'main.js'), 'utf8');
const polyfillLoadIndex = entrySource.indexOf("require('./runtime-polyfills.js')");
const serverLoadIndex = entrySource.indexOf("require('./android-server.js')");
assert(polyfillLoadIndex >= 0, 'main.js 未加载 Node 18 兼容层');
assert(serverLoadIndex > polyfillLoadIndex, '兼容层必须早于 android-server.js 加载');

await import(pathToFileURL(resolve(runtimeRoot, 'runtime-polyfills.js')).href);

const original = [3, 1, 2];
assert.deepEqual(original.toReversed(), [2, 1, 3]);
assert.deepEqual(original.toSorted((a, b) => a - b), [1, 2, 3]);
assert.deepEqual(original, [3, 1, 2], 'immutable Array polyfill 不应修改原数组');

assert.deepEqual(
  new Map([
    ['a', 1],
    ['b', 2],
    ['c', 3],
  ])
    .values()
    .filter((value) => value > 1)
    .map((value) => value * 2)
    .toArray(),
  [4, 6],
);
assert.equal(new Set([1, 2, 3]).values().reduce((sum, value) => sum + value, 0), 6);
assert.equal(new Set([1, 2, 3]).values().some((value) => value === 2), true);

const mapUpsert = new Map([['present', 7]]);
assert.equal(mapUpsert.getOrInsert('present', 9), 7);
assert.equal(mapUpsert.getOrInsert('new', 9), 9);
let computedCalls = 0;
assert.equal(
  mapUpsert.getOrInsertComputed('computed', (key) => {
    computedCalls += 1;
    return key.length;
  }),
  8,
);
assert.equal(mapUpsert.getOrInsertComputed('computed', () => 0), 8);
assert.equal(computedCalls, 1);

function splitPackageSpecifier(specifier) {
  const parts = specifier.split('/');
  const packageName = specifier.startsWith('@')
    ? `${parts[0]}/${parts[1]}`
    : parts[0];
  const subpathParts = specifier.startsWith('@') ? parts.slice(2) : parts.slice(1);
  return {
    packageName,
    exportKey: subpathParts.length === 0 ? '.' : `./${subpathParts.join('/')}`,
  };
}

function selectImportTarget(exportsMap, exportKey) {
  const exact = exportsMap?.[exportKey];
  const wildcard = exact === undefined
    ? Object.entries(exportsMap ?? {}).find(([key]) =>
        key.includes('*') &&
        exportKey.startsWith(key.slice(0, key.indexOf('*'))) &&
        exportKey.endsWith(key.slice(key.indexOf('*') + 1)),
      )
    : undefined;
  let target = exact;
  if (target === undefined && wildcard) {
    const [pattern, mapped] = wildcard;
    const prefix = pattern.slice(0, pattern.indexOf('*'));
    const suffix = pattern.slice(pattern.indexOf('*') + 1);
    const replacement = exportKey.slice(prefix.length, exportKey.length - suffix.length);
    target = typeof mapped === 'string' ? mapped.replace('*', replacement) : mapped;
  }
  if (target && typeof target === 'object') {
    target = target.import ?? target.default;
  }
  assert.equal(typeof target, 'string', `缺少 ESM 导出：${exportKey}`);
  return target;
}

async function importRuntimePackage(specifier) {
  const { packageName, exportKey } = splitPackageSpecifier(specifier);
  const packageJson = resolve(runtimeRoot, 'node_modules', packageName, 'package.json');
  const pkg = JSON.parse(readFileSync(packageJson, 'utf8'));
  const target = selectImportTarget(pkg.exports, exportKey);
  return import(pathToFileURL(resolve(dirname(packageJson), target)).href);
}

const adapters = await importRuntimePackage('@dan-uni/dan-any/adapters');
const pureCore = await importRuntimePackage('@dan-uni/dan-any/core/main/pure');

for (const exportName of [
  'ArtplayerMetadata',
  'BiliXmlMetadata',
  'DanuniJsonMetadata',
  'DdplayMetadata',
  'VodMetadata',
]) {
  assert.equal(
    typeof adapters[exportName]?.type,
    'string',
    `@dan-uni/dan-any 缺少可用导出：${exportName}.type`,
  );
}
assert.equal(typeof pureCore.UniDB, 'function', 'UniDB 导出不可用');

const parsedBili = adapters.BiliCommonParser(
  { $UniDB: { DMIDGenerator: () => 'smoke-dmid' } },
  {
    id: 1n,
    idStr: '1',
    oid: 1n,
    progress: 1000,
    mode: 1,
    fontsize: 25,
    color: 0xffffff,
    midHash: '0123456789abcdef',
    content: 'smoke',
    ctime: 1n,
    weight: 1,
    pool: 0,
    attr: 0,
  },
);
assert.equal(parsedBili.mode, 'Normal');
assert.deepEqual(parsedBili.attr, []);

const db = new pureCore.UniDB().init();
const chunk = db.makeChunk({});
chunk.upsertDanmakus(
  [
    {
      SOID: 'smoke-source',
      progress: 1000,
      mode: 'Normal',
      fontsize: 25,
      color: 0xffffff,
      senderID: 'smoke-sender',
      content: 'smoke-content',
      ctime: new Date(0),
      weight: 1,
      pool: 'Def',
      attr: ['Protect', 'HighLike'],
      platform: 'smoke',
      extra: null,
    },
  ],
  true,
);
assert.equal(chunk.$count, 1);
assert.equal(typeof chunk.$danmakus[0].DMID, 'string');
assert(chunk.$danmakus[0].DMID.length > 0);
assert.equal(db.listChunks().length, 1);

const { ConverterFactory } = await importRuntimePackage('opencc-js/core');
const { default: simplifiedToTraditionalCharacters } =
  await importRuntimePackage('opencc-js/dict/STCharacters');
const { default: toSimplifiedChinese } =
  await importRuntimePackage('opencc-js/to/cn');
const { default: toTraditionalChinese } =
  await importRuntimePackage('opencc-js/to/tw');

const toSimplified = ConverterFactory(toSimplifiedChinese);
const toTraditional = ConverterFactory(
  [simplifiedToTraditionalCharacters],
  toTraditionalChinese,
);

assert.equal(toSimplified('漢語'), '汉语');
assert.equal(toTraditional('汉语'), '漢語');
console.log('Core runtime dependency smoke: OK');
