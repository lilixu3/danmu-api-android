/**
 * ass-danmaku loader（DanmuApiApp 专用装配层，MIT）
 *
 * vendored 自 tiansh/ass-danmaku @ master（MPL-2.0，见同目录 LICENSE）：
 *   - layout.js：弹幕轨道防碰撞布局（滚动/顶部/底部）
 *   - ass.js：ASS 字幕文本生成
 *
 * 上游文件为 WebExtension 形态（挂载到 window.danmaku；ass.js 引用
 * browser.i18n）。此处提供最小 window/browser 桩并读取装配结果，
 * vendor 文件本体保持上游原样以便后续同步。
 *
 * 上游来源：https://github.com/tiansh/ass-danmaku
 */
'use strict';

(function () {
  if (typeof globalThis.window === 'undefined') globalThis.window = globalThis;
  const w = globalThis.window;

  // 文本宽度度量桩：CJK 记整宽，其余记半宽（嵌入环境无字体度量能力）
  w.font = w.font || {
    text(family, text, size) {
      let width = 0;
      for (const ch of String(text)) {
        width += ch.codePointAt(0) > 0x2e80 ? size : size * 0.55;
      }
      return Math.ceil(width);
    },
  };

  // WebExtension i18n 桩（ass.js 的 Original Script 行占位）
  w.browser = w.browser || { i18n: { getMessage: () => '' } };

  require('./layout.js');
  require('./ass.js');
})();

const layout = globalThis.window.danmaku.layout;
const render = globalThis.window.danmaku.ass;

const DEFAULT_OPTIONS = {
  resolutionX: 1280,
  resolutionY: 720,
  fontFamily: 'sans-serif',
  fontSize: 1.0,
  textOpacity: 100,
  rtlDuration: 10,
  fixDuration: 5,
  maxDelay: 6,
  maxOverlap: 1,
  bottomReserved: 40,
};

const MODE_MAP = [null, 'RTL', null, 'RTL', 'BOTTOM', 'TOP'];

/**
 * 将核心弹幕 JSON（/api/v2/comment 的 comments 结构）转换为
 * ass-danmaku 的中间 danmaku 结构。
 */
function commentsToDanmaku(rawText, meta) {
  const data = JSON.parse(rawText);
  const comments = Array.isArray(data) ? data : data.comments || [];
  const danmaku = [];
  for (const comment of comments) {
    const parts = String(comment.p || '').split(',');
    const mode = Number(parts[1]);
    const style = MODE_MAP[mode];
    if (!style || !comment.m) continue;
    const color = Number(parts[3]) || 0xffffff;
    danmaku.push({
      text: String(comment.m),
      time: Number(parts[0]) || 0,
      mode: style,
      size: Number(parts[2]) || 25,
      color: {
        r: (color >> 16) & 255,
        g: (color >> 8) & 255,
        b: color & 255,
      },
      bottom: false,
    });
  }
  danmaku.meta = {
    name: meta?.name || 'DanmuApiApp',
    url: meta?.url || '',
  };
  return danmaku;
}

/**
 * 弹幕 JSON → ASS 字幕全文（异步：布局算法分批让路事件循环）。
 */
async function renderDanmakuAss(rawText, meta, optionOverrides) {
  const options = { ...DEFAULT_OPTIONS, ...(optionOverrides || {}) };
  const lines = await layout(commentsToDanmaku(rawText, meta), options);
  return render({ meta: { name: meta?.name || 'DanmuApiApp', url: meta?.url || '' }, layout: lines }, options);
}

module.exports = { commentsToDanmaku, renderDanmakuAss, DEFAULT_OPTIONS };
