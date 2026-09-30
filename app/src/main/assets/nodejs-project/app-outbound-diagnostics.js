'use strict';
const { eligible } = require('./app-outbound-bridge.js');
const targets = {
  bahamut: ['https://api.gamer.com.tw/mobile_app/anime/v1/search.php?kw=' + encodeURIComponent('葬送的芙莉蓮')],
  tmdb: ['https://api.tmdb.org/3/configuration'],
  dandan: ['https://api.danmaku.weeblify.app/ddp/v1?path=/v2/search/anime?keyword=' + encodeURIComponent('葬送的芙莉莲')],
  animeko: [
    'https://api.animeko.org/v2/subjects/400602',
    'https://danmaku-global.myani.org/v2/subjects/400602',
    'https://danmaku-cn.myani.org/v2/subjects/400602',
    'https://s1.animeko.openani.org/v2/subjects/400602',
    'https://api.bangumi.vip/v0/episodes?subject_id=400602&limit=1',
  ],
};
async function diagnoseSource(source, snapshot, env, { signal, fetchImpl = globalThis.fetch } = {}) {
  const candidates = targets[source];
  if (!candidates) throw new Error('Invalid source');
  if (!candidates.every(target => eligible(new URL(target), snapshot, env))) {
    return { ok:false, reason:'该来源已配置代理，优先使用原有出口', httpStatus:409 };
  }
  const started = Date.now();
  const deadline = started + 15000;
  let status, reason = '未能连接到源站';
  for (const target of candidates) {
    signal?.throwIfAborted();
    const remaining = deadline - Date.now();
    if (remaining <= 0) break;
    try {
      const response = await fetchImpl(target, {
        headers:{'User-Agent':'danmu-api-android/connectivity'},
        signal:AbortSignal.any([...(signal ? [signal] : []), AbortSignal.timeout(Math.min(remaining, source === 'animeko' ? 3500 : 15000))]),
      });
      status = response.status;
      const data = await response.json();
      const valid = source === 'tmdb' ? [200,401].includes(status)
        : status === 200 && (source === 'bahamut' ? Array.isArray(data.anime)
          : source === 'dandan' ? Array.isArray(data.animes)
          : Boolean(data.id) || Array.isArray(data.data));
      if (valid) return { ok:true, status, durationMs:Date.now()-started };
      reason = '源站响应异常';
    } catch (error) {
      signal?.throwIfAborted();
      reason = '连接失败或超时';
    }
  }
  return { ok:false, status, reason, durationMs:Date.now()-started };
}
module.exports = { diagnoseSource, diagnosticSources:Object.keys(targets) };
