import assert from 'node:assert/strict';
import { readFileSync, existsSync, readdirSync } from 'node:fs';
import { resolve } from 'node:path';

const projectRoot = resolve('app/src/main/assets/nodejs-project');
const nodeModulesRoot = resolve(projectRoot, 'node_modules');
const lock = JSON.parse(readFileSync(resolve(projectRoot, 'package-lock.json'), 'utf8'));
const packageRoots = new Set();

for (const lockPath of Object.keys(lock.packages ?? {})) {
  if (!lockPath.startsWith('node_modules/')) continue;
  const relative = lockPath.slice('node_modules/'.length);
  const parts = relative.split('/');
  if (parts[0].startsWith('@')) {
    if (parts.length >= 2) packageRoots.add(`${parts[0]}/${parts[1]}`);
  } else if (parts[0]) {
    packageRoots.add(parts[0]);
  }
}

assert(packageRoots.has('@dan-uni/dan-any'));
assert(packageRoots.has('opencc-js'));
assert(packageRoots.size > 2, '必须验证完整传递依赖闭包，而不是只有两个直接包');

const missing = [...packageRoots]
  .sort()
  .filter((name) => !existsSync(resolve(projectRoot, 'node_modules', name, 'package.json')));
assert.deepEqual(missing, [], `基础运行时缺少锁文件包：${missing.join(', ')}`);

const runtimeNoise = [];
function scanRuntimeNoise(directory) {
  for (const entry of readdirSync(directory, { withFileTypes: true })) {
    const absolute = resolve(directory, entry.name);
    if (entry.isDirectory()) {
      scanRuntimeNoise(absolute);
      continue;
    }
    const lower = entry.name.toLowerCase();
    if (
      lower.endsWith('.d.ts') ||
      lower.endsWith('.d.cts') ||
      lower.endsWith('.d.mts') ||
      lower.endsWith('.map') ||
      lower === 'readme' ||
      lower.startsWith('readme.') ||
      lower.startsWith('readme-') ||
      lower === 'changelog' ||
      lower.startsWith('changelog.')
    ) {
      runtimeNoise.push(absolute.slice(nodeModulesRoot.length + 1));
    }
  }
}
scanRuntimeNoise(nodeModulesRoot);
assert.deepEqual(runtimeNoise, [], `基础运行时混入非运行时文件：${runtimeNoise.slice(0, 20).join(', ')}`);

console.log(`Bundled lock closure smoke: OK (${packageRoots.size} packages)`);
