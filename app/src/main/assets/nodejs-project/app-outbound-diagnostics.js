'use strict';
const { eligible } = require('./app-outbound-bridge.js');
const targets = {
  bahamut: ['https://api.gamer.com.tw/mobile_app/anime/v1/search.php?kw=' + encodeURIComponent('葬送的芙莉蓮')],
  tmdb: ['https://api.tmdb.org/3/configuration'],
  // The core calls nav before its WBI-signed search; legacy unsigned search is often rejected with 412.
  bilibili: ['https://api.bilibili.com/x/web-interface/nav'],
  dandan: ['https://api.danmaku.weeblify.app/ddp/v1?path=/v2/search/anime?keyword=' + encodeURIComponent('葬送的芙莉莲')],
  animeko: [
    'https://api.bgm.tv/v0/subjects/400602',
    'https://api.bangumi.vip/v0/subjects/400602',
    'https://api.animeko.org/v2/subjects/400602',
    'https://danmaku-global.myani.org/v2/subjects/400602',
    'https://danmaku-cn.myani.org/v2/subjects/400602',
    'https://s1.animeko.openani.org/v2/subjects/400602',
    'https://api.bangumi.vip/v0/episodes?subject_id=400602&limit=1',
  ],
  // Search may reject anonymous callers independently of network reachability.
  // Check a public detail document; Cookie remains optional, as in the core.
  douban: ['https://m.douban.com/rexxar/api/v2/movie/36296618?for_mobile=1'],
};
function diagnosticRequest(source, target, env) {
  // Match the core's TMDB_API_KEY setting; keep credentials out of public results.
  const url = new URL(target);
  if (source === 'tmdb' && String(env.TMDB_API_KEY || '').trim()) {
    url.searchParams.set('api_key', String(env.TMDB_API_KEY).trim());
  }
  return {url:url.href,headers:source==='douban' ? {
    'User-Agent':'Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/91.0.4472.124 Safari/537.36',
    Referer:'https://m.douban.com/movie/','Content-Type':'application/json',
    ...(env.DOUBAN_COOKIE ? {Cookie:String(env.DOUBAN_COOKIE)} : {}),
  } : source==='bilibili' ? {
    'User-Agent':'Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36',
    Referer:'https://www.bilibili.com/',
    Cookie:String(env.BILIBILI_COOKIE || ''),
  } : {'User-Agent':'danmu-api-android/connectivity'}};
}
function validBilibiliNav(data) {
  if (!data || typeof data !== 'object' || !data.data || typeof data.data !== 'object') return false;
  if (data.code === 0) return true;
  // Anonymous nav replies carry -101 (not logged in) while still serving WBI keys.
  const wbi = data.data.wbi_img;
  return data.code === -101 && data.data.isLogin === false
    && typeof wbi?.img_url === 'string' && wbi.img_url.trim().length > 0
    && typeof wbi?.sub_url === 'string' && wbi.sub_url.trim().length > 0;
}
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
      const request=diagnosticRequest(source,target,env);
      const response = await fetchImpl(request.url, {
        headers:request.headers,
        signal:AbortSignal.any([...(signal ? [signal] : []), AbortSignal.timeout(Math.min(remaining, source === 'animeko' ? 3500 : 15000))]),
      });
      status = response.status;
      const text=await response.text();
      if(status===403 && (/\berror[\s_-]*code\s*[:=]\s*['"]?1009\b/i.test(text) || /Error\s*1009[\s\S]{0,150}(?:Access Denied|country|region)/i.test(text))) {
        return {ok:false,status,reason:'源站限制当前 IP 所属地区，增强直连无法改变出口地区，请为该来源配置代理',durationMs:Date.now()-started};
      }
      if(source==='douban' && [401,403,429].includes(status)) {
        return {ok:false,status,reason:status===429 ? '豆瓣请求过于频繁，请稍后重试' : '豆瓣公开接口本次拒绝请求，请稍后重试',durationMs:Date.now()-started};
      }
      if (source === 'tmdb' && status === 401) {
        const configurationRequired = !String(env.TMDB_API_KEY || '').trim();
        return {ok:configurationRequired,status,configurationRequired,
          reason:configurationRequired ? '已响应，但 API Key 未配置' : '已响应，但 API Key 无效或无权限',
          durationMs:Date.now()-started};
      }
      if(status>=400) {reason=`源站返回 HTTP ${status}`;continue;}
      let data;try{data=JSON.parse(text);}catch{reason='源站返回非预期内容，可能需要验证';continue;}
      const valid = source === 'tmdb' ? status === 200
        : status === 200 && (source === 'bahamut' ? Array.isArray(data.anime)
          : source === 'bilibili' ? validBilibiliNav(data)
          : source === 'dandan' ? Array.isArray(data.animes)
          : source === 'douban' ? !data.banned && String(data.id)==='36296618' && typeof data.title==='string' && data.title.trim().length>0
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
module.exports = { diagnoseSource, diagnosticSources:Object.keys(targets), diagnosticTargets:targets, diagnosticRequest, validBilibiliNav };
