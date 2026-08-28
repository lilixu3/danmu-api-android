import assert from 'node:assert/strict';
import { readFileSync } from 'node:fs';
import { resolve } from 'node:path';

const { commentsToDanmaku, renderDanmakuAss } = await import(
  new URL('../app/src/main/assets/nodejs-project/lib/ass-danmaku/index.js', import.meta.url).href
);

const sample = JSON.stringify({
  count: 4,
  comments: [
    { p: '0.00,1,25,16777215,[migu]', m: '滚动弹幕' },
    { p: '1.50,5,25,16711680,[migu]', m: '顶部{固定}' },
    { p: '2.00,4,18,65280,[migu]', m: '底部绿弹' },
    { p: '3.00,7,25,16777215,[migu]', m: '高级弹幕（v1 跳过）' },
  ],
});

// 1. 中间结构映射：mode 1/5/4 保留，7（高级弹幕）跳过
const danmaku = commentsToDanmaku(sample, { name: '回归测试', url: 'test://a' });
assert.equal(danmaku.length, 3, 'mode 7 应被跳过');
assert.equal(danmaku[0].mode, 'RTL');
assert.equal(danmaku[1].mode, 'TOP');
assert.deepEqual(danmaku[1].color, { r: 255, g: 0, b: 0 });

// 2. 端到端：ASS 结构与关键语义
const ass = await renderDanmakuAss(sample, { name: '回归测试', url: 'test://a' }, {});
assert.ok(ass.startsWith('[Script Info]'), 'ASS 必须以 [Script Info] 开头');
assert.ok(ass.includes('PlayResX: 1280'), 'PlayResX 固定 1280');
assert.ok(ass.includes('Style: Fix,'), '固定弹幕 Style 存在');
const dialogueLines = ass.split('\r\n').filter(l => l.startsWith('Dialogue:'));
assert.equal(dialogueLines.length, 3, '三条有效弹幕对应三行 Dialogue');

// 滚动弹幕：\move 从屏外右侧进入左侧
assert.ok(dialogueLines[0].includes('\\move(1330,'), '滚动弹幕应使用 \\move 且起点在屏外右侧');
// 顶部固定：\pos 居中
assert.ok(dialogueLines[1].includes('\\pos(640,'), '顶部弹幕应使用 \\pos 水平居中');
// 颜色：0xFF0000（红）→ ASS BGR &H0000FF
assert.ok(dialogueLines[1].includes('&H0000FF'), '红色应转换为 ASS BGR 形式');
// 时间轴：time 1.5 → 00:00:01.50
assert.ok(dialogueLines[1].includes('00:00:01.50'), '时间轴格式应为 h:mm:ss.cc');

// 3. 特殊字符转义：{} 转全角，避免被解析为 ASS 覆盖标签
assert.ok(dialogueLines[1].includes('｛固定｝'), '花括号应转义为全角');

// 4. 容错：空弹幕与非法 JSON
const emptyAss = await renderDanmakuAss(JSON.stringify({ count: 0, comments: [] }), {});
assert.equal((emptyAss.match(/Dialogue:/g) || []).length, 0, '空弹幕应输出 0 行 Dialogue');
assert.throws(() => commentsToDanmaku('not-json', {}), SyntaxError);

// 5. App 转发层接线存在（防误删）
const serverSource = readFileSync(
  resolve('app/src/main/assets/nodejs-project/android-server.js'),
  'utf8',
);
assert.ok(serverSource.includes("searchParams.get('format') === 'ass'"), 'android-server.js 缺少 format=ass 拦截');
assert.ok(serverSource.includes('_assDanmaku.renderDanmakuAss'), 'android-server.js 缺少 ASS 转换调用');
assert.ok(serverSource.includes("searchParams.set('format', 'json')"), 'format=ass 必须改写核心请求为 json');

console.log('Danmaku ASS regression: OK');
