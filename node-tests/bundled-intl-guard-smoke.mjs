import assert from 'node:assert/strict';
import { readFileSync, readdirSync, existsSync } from 'node:fs';
import { resolve } from 'node:path';

/**
 * bundled-intl-guard-smoke.mjs
 *
 * 内嵌 Node 运行时为 --with-intl=none（digidem lite 变体），globalThis.Intl
 * 不存在。任何依赖如果在运行路径上引用 `Intl.*` 都会在真机上直接崩溃。
 *
 * 本测试静态扫描随包分发的全部 JS，凡是出现 Intl.* / toLocale* 的文件必须
 * 出现在白名单里（附原因），否则视为新引入的 ICU 依赖并阻断构建。
 *
 * 白名单维护规则：新条目必须先在 `--with-intl=none` 语义下确认其 Intl 调用
 * 处于「永不执行的分支」，并在注释中说明依据后才能登记。
 */

const runtimeRoot = resolve('app/src/main/assets/nodejs-project');
const nodeModulesRoot = resolve(runtimeRoot, 'node_modules');

// 文件相对 node_modules 的白名单；值为允许理由。
const allowedIntlFiles = new Map([
  // fast-xml-parser 的可选货币数值解析器；Intl.NumberFormat 只在其 parse()
  // 内部调用，而 dan-any/弹幕链路的 XMLParser 配置从未启用该 valueParser，
  // 模块加载与默认解析均不会触达。实测在 delete globalThis.Intl 后主入口
  // 与默认解析均正常（见 git 历史验收记录）。
  ['fast-xml-parser/src/v6/valueParsers/currency.js', 'currency valueParser 未被任何运行配置启用'],
]);

function collectJsFiles(dir) {
  const out = [];
  for (const entry of readdirSync(dir, { withFileTypes: true })) {
    const absolute = resolve(dir, entry.name);
    if (entry.isDirectory()) {
      out.push(...collectJsFiles(absolute));
      continue;
    }
    if (/\.(cjs|mjs|js)$/.test(entry.name)) out.push(absolute);
  }
  return out;
}

assert.ok(existsSync(nodeModulesRoot), '缺少内置 node_modules');

const intlPattern = /\bIntl\.[A-Z]|new Intl\b/;
const violations = [];
for (const absolute of collectJsFiles(nodeModulesRoot)) {
  const relative = absolute.slice(nodeModulesRoot.length + 1);
  if (allowedIntlFiles.has(relative)) continue;
  if (intlPattern.test(readFileSync(absolute, 'utf8'))) violations.push(relative);
}

assert.deepEqual(
  violations,
  [],
  `lite 运行时（--with-intl=none）不允许新的 Intl 依赖：${violations.join(', ')}`,
);

for (const [file] of allowedIntlFiles) {
  assert.ok(
    existsSync(resolve(nodeModulesRoot, file)),
    `白名单文件已被移动或删除，请重新评估：${file}`,
  );
}

console.log(`Bundled intl guard smoke: OK (${allowedIntlFiles.size} whitelisted files)`);
