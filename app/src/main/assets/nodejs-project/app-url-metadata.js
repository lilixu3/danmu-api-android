'use strict';

const path = require('node:path');
const { pathToFileURL } = require('node:url');
const { withSource } = require('./app-proxy-routing.js');
const UA = 'Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 Chrome/125.0.0.0 Safari/537.36';
const platforms = [
  ['bilibili', '哔哩哔哩', ['bilibili.com', 'b23.tv', 'bili2233.cn']],
  ['tencent', '腾讯视频', ['qq.com']], ['iqiyi', '爱奇艺', ['iqiyi.com', 'iqiyi.cn']],
  ['youku', '优酷', ['youku.com']], ['imgo', '芒果TV', ['mgtv.com']],
  ['migu', '咪咕视频', ['miguvideo.com']], ['sohu', '搜狐视频', ['sohu.com']],
  ['leshi', '乐视视频', ['le.com']], ['xigua', '西瓜视频', ['ixigua.com', 'douyin.com']],
  ['maiduidui', '埋堆堆', ['mddcloud.com.cn']], ['aiyifan', '爱壹帆', ['yfsp.tv']],
  ['hongguo', '红果短剧', ['hongguoduanju.com']],
  ['animeko', 'Animeko', ['bgm.tv', 'bangumi.tv', 'bangumi.lol', 'chii.in']],
  ['bahamut', '巴哈姆特', ['ani.gamer.com.tw']]
];
const text = value => value == null ? '' : String(value).trim();
const year = value => text(value).match(/(?:19|20)\d{2}/)?.[0] || '';
const list = value => Array.isArray(value) ? value : [];
const json = value => typeof value === 'string' ? JSON.parse(value) : value;
function platformFor(value) {
  try {
    const u = new URL(value);
    if (!['http:', 'https:'].includes(u.protocol) || u.username || u.password || (u.port && !['80', '443'].includes(u.port))) return null;
    return platforms.find(([, , domains]) => domains.some(d => u.hostname === d || u.hostname.endsWith('.' + d))) || null;
  } catch { return null; }
}
function absolute(value, base) {
  try { const u = new URL(text(value), base); return value && ['https:', 'http:'].includes(u.protocol) ? u.href : ''; } catch { return ''; }
}
function decode(value) {
  return text(value).replace(/&amp;/g, '&').replace(/&quot;/g, '"').replace(/&#39;/g, "'").replace(/&lt;/g, '<').replace(/&gt;/g, '>')
    .replace(/\\u([\da-f]{4})/ig, (_, code) => String.fromCharCode(parseInt(code, 16))).replace(/\\\//g, '/');
}
function meta(html, key) {
  for (const tag of text(html).match(/<meta\b[^>]*>/ig) || []) {
    const attrs = {};
    for (const m of tag.matchAll(/([\w:-]+)\s*=\s*(["'])(.*?)\2/gs)) attrs[m[1].toLowerCase()] = decode(m[3]);
    if ([attrs.property, attrs.name, attrs.itemprop].includes(key)) return attrs.content || '';
  }
  return '';
}
function pageMeta(html, url) {
  // Explicit page metadata only: never scan arbitrary title/pic fields across recommendations.
  const declared = meta(html, 'og:url');
  if (declared && identity(declared) !== identity(url)) return {};
  return {
    title: meta(html, 'og:title') || decode(text(html).match(/<title[^>]*>(.*?)<\/title>/is)?.[1]),
    posterUrl: absolute(meta(html, 'og:image') || meta(html, 'twitter:image'), url),
    year: year(meta(html, 'video:release_date') || meta(html, 'datePublished'))
  };
}
function objectState(html, name) {
  const marker = new RegExp('(?:window\\.)?' + name + '\\s*=\\s*').exec(html);
  if (!marker) return '';
  const start = marker.index + marker[0].length;
  if (html[start] !== '{') return '';
  let depth = 0, quote = '', escaped = false;
  for (let i = start; i < html.length; i++) {
    const ch = html[i];
    if (quote) {
      if (escaped) escaped = false;
      else if (ch === '\\') escaped = true;
      else if (ch === quote) quote = '';
    } else if (ch === '"' || ch === "'") quote = ch;
    else if (ch === '{') depth++;
    else if (ch === '}' && --depth === 0) return html.slice(start, i + 1);
  }
  return '';
}
function field(state, name) {
  return decode(text(state).match(new RegExp('(?:["\\\']|\\b)' + name + '["\\\']?\\s*:\\s*(["\\\'])(.*?)\\1', 's'))?.[2]);
}
function identity(value) {
  try {
    const u = new URL(value), source = platformFor(value)?.[0];
    const q = key => u.searchParams.get(key) || '';
    switch (source) {
      case 'bilibili': return `bilibili:${u.pathname.match(/(?:BV[\w]+|av\d+|ep\d+|ss\d+)/)?.[0] || u.pathname}:p${q('p') || 1}`;
      case 'tencent': return `tencent:${q('vid') || u.pathname.match(/\/([^/.]+)\.html/)?.[1] || u.pathname}`;
      case 'iqiyi': return `iqiyi:${u.pathname.match(/v_([^/.]+)/)?.[1] || u.pathname}`;
      case 'youku': return `youku:${u.pathname.match(/id_([^/.]+)/)?.[1] || u.pathname}`;
      case 'imgo': return `imgo:${u.pathname.match(/\/(\d+)\.html/)?.[1] || u.pathname}`;
      case 'migu': return `migu:${q('cid')}`;
      case 'xigua': return `xigua:${u.pathname.match(/\d{10,}/)?.[0] || u.pathname}`;
      case 'maiduidui': return `maiduidui:${q('uuid') || u.pathname}`;
      case 'aiyifan': return `aiyifan:${u.pathname}:${q('id')}`;
      case 'bahamut': return `bahamut:${q('sn')}`;
      default: return `${source || u.hostname}:${u.pathname.replace(/\/$/, '')}`;
    }
  } catch { return ''; }
}
function episode(id, title, url, number, extra = {}) {
  return { id: text(id), title: text(title), url, number: Number(number) || 1, label: '', group: '', posterUrl: '', ...extra };
}
function finish(result, inputUrl, canonicalUrl, platform) {
  let episodes = list(result.episodes).filter(ep => ep.id && ep.url && platformFor(ep.url)?.[0] === platform[0]);
  episodes = episodes.filter((ep, i) => episodes.findIndex(other => identity(other.url) === identity(ep.url)) === i);
  const selected = episodes.find(ep => result.selectedEpisodeId ? ep.id === result.selectedEpisodeId : identity(ep.url) === identity(canonicalUrl));
  const missing = episodes.length && !selected && !result.isSeries;
  // A list without the requested episode must not silently replace the original link with episode 1.
  if (missing) episodes = [];
  return {
    version: 1, inputUrl, canonicalUrl: selected?.url || canonicalUrl, platformLabel: platform[1],
    title: text(result.title), posterUrl: absolute(result.posterUrl, canonicalUrl), year: text(result.year),
    episodeTitle: selected?.title || text(result.episodeTitle) || text(result.title),
    episodeLabel: selected?.label || '', selectedEpisodeId: selected?.id || '', episodes,
    episodesComplete: !!result.episodesComplete && !missing && episodes.length > 0,
    notice: missing ? '未找到链接指定的分集，保留原链接获取弹幕' :
      !episodes.length ? '仅显示当前链接，完整分集暂不可用' :
      result.episodesComplete ? '' : `已获取 ${episodes.length} 个播放条目，分集完整性尚未确认`
  };
}

async function loadCore(directory) {
  const load = rel => import(pathToFileURL(path.join(directory, rel)).href);
  const http = await load('utils/http-util.js');
  let registry;
  try { registry = await load('sources/registry.js'); } catch { /* Older cores use individual source classes. */ }
  let globals = {};
  try { globals = (await load('configs/globals.js')).globals || {}; } catch {}
  return {
    async get(url) {
      const headers = { 'User-Agent': UA, Referer: new URL(url).origin + '/' };
      if (platformFor(url)?.[0] === 'bilibili' && globals.bilibliCookie) headers.Cookie = globals.bilibliCookie;
      const response = await http.httpGet(url, { headers, timeout: 8000, retries: 0 });
      if (response?.status && (response.status < 200 || response.status >= 300)) throw new Error('Metadata HTTP failure');
      return response?.data;
    },
    async source(key) {
      const instance = registry?.getSourceByKey?.(key);
      if (instance) return instance;
      const Source = (await load(`sources/${key === 'imgo' ? 'mango' : key}.js`)).default;
      return new Source();
    },
    run(key, action) { return withSource(key, () => http.sourceLogContext?.run ? http.sourceLogContext.run(key === 'imgo' ? 'mango' : key, action) : action()); }
  };
}

async function resolveUrlMetadata(inputUrl, deps) {
  const platform = platformFor(inputUrl);
  if (!platform) return null;
  return (deps.run || ((_, action) => action()))(platform[0], async () => {
    let url = inputUrl;
    const get = async target => json(await deps.get(target));
    const source = await Promise.resolve().then(() => deps.source(platform[0])).catch(() => null);
    if (['b23.tv', 'bili2233.cn'].includes(new URL(url).hostname) && source?.resolveB23Link) {
      url = await source.resolveB23Link(url);
      if (platformFor(url)?.[0] !== 'bilibili') return null;
    }
    const u = new URL(url), q = key => u.searchParams.get(key), match = pattern => u.pathname.match(pattern)?.[1];
    const episodesFor = async id => {
      try { return id && source?.getEpisodes ? await source.getEpisodes(id) : []; }
      catch { return []; }
    };
    let result = {}, html;
    const page = async () => {
      if (html === undefined) {
        try { html = text(await deps.get(url)); } catch { html = ''; }
      }
      return pageMeta(html, url);
    };
    switch (platform[0]) {
      case 'bilibili': {
        const isVideo = /\/video\/(?:BV\w+|av\d+)/.test(u.pathname);
        const ep = match(/\/ep(\d+)/) || (!isVideo && q('ep_id')), ss = match(/\/ss(\d+)/) || (!isVideo && q('season_id'));
        if (ep || ss) {
          const data = await get(`https://api.bilibili.com/pgc/view/web/season?${ep ? 'ep_id=' + ep : 'season_id=' + ss}`);
          if (data?.code !== 0 || !data.result) break;
          const r = data.result, title = r.season_title || r.title;
          if (!ep && ss && r.season_id != null && text(r.season_id) !== ss) break;
          const map = (ep, i, group = '') => episode(ep.id || ep.ep_id,
            [title, /^\d+(\.\d+)?$/.test(ep.title) ? `第${ep.title}集` : ep.title, ep.long_title].filter(Boolean).join(' '),
            `https://www.bilibili.com/bangumi/play/ep${ep.id || ep.ep_id}`, Number(ep.title) || i + 1,
            { label: /^\d+(\.\d+)?$/.test(ep.title) ? `第${ep.title}集` : text(ep.title), group });
          const episodes = list(r.episodes || r.main_section?.episodes).map((e, i) => map(e, i));
          for (const section of list(r.section)) episodes.push(...list(section.episodes).map((e, i) => map(e, i, section.title)));
          result = { title, posterUrl: r.cover, year: year(r.publish?.pub_time), episodes,
            selectedEpisodeId: ep || '', isSeries: !ep, episodesComplete: true };
        } else {
          const bv = match(/\/(BV\w+)/), av = match(/\/av(\d+)/);
          if (!bv && !av) break;
          const response = await get(`https://api.bilibili.com/x/web-interface/view?${bv ? 'bvid=' + bv : 'aid=' + av}`);
          const d = response?.data;
          if (response?.code !== 0 || !d || (bv && d.bvid !== bv) || (av && text(d.aid) !== av)) break;
          const pages = list(d.pages), selected = pages.find(p => p.page === Number(q('p') || 1));
          result = { title: d.title, posterUrl: d.pic, year: d.pubdate ? String(new Date(d.pubdate * 1000).getUTCFullYear()) : '',
            episodes: pages.map(p => episode(p.cid, p.part || d.title, `https://www.bilibili.com/video/${d.bvid}?p=${p.page}`, p.page,
              { label: pages.length > 1 ? `P${p.page}` : '正片' })),
            selectedEpisodeId: selected ? text(selected.cid) : '', episodesComplete: true };
          // AV URLs resolve to the returned BV identity; invalid P remains unselected.
          if (selected) url = `https://www.bilibili.com/video/${d.bvid}?p=${selected.page}`;
        }
        break;
      }
      case 'tencent': {
        result = await page();
        const cid = match(/\/x\/cover\/([^/.]+)/) || q('cid');
        result.isSeries = !!cid && !q('vid') && !/\/x\/cover\/[^/]+\/[^/]+/.test(u.pathname);
        const state = objectState(html, 'COVER_INFO');
        if (cid && [field(state, 'id'), field(state, 'cid')].includes(cid)) {
          result.title = field(state, 'title') || result.title;
          result.posterUrl = field(state, 'new_pic_vt') || field(state, 'pic') || result.posterUrl;
          result.year = year(field(state, 'year')) || result.year;
        }
        result.episodes = list(await episodesFor(cid)).map((e, i) => episode(e.vid,
          source?.buildEpisodeTitle?.(e) || e.playTitle || e.unionTitle || e.title || `第${i + 1}集`,
          `https://v.qq.com/x/cover/${cid}/${e.vid}.html`, i + 1));
        break;
      }
      case 'iqiyi': {
        const id = match(/\/v_([\w]+)/);
        if (!id) break;
        const entity = source?._videoIdToEntityId?.(id);
        const data = entity && source?._fetchBaseInfoData ? await source._fetchBaseInfoData(entity) : null;
        const b = data?.data?.base_data;
        if (b) result = { title: b.title, episodeTitle: b.current_video_title, posterUrl: b.image_url || b.horizontal_image_url, year: year(b.current_video_year || b.publish_date) };
        result.episodes = list(await episodesFor(id)).map((e, i) => episode(e.id, e.title, e.link, e.order || i + 1));
        break;
      }
      case 'youku': {
        const id = match(/id_([^/.]+)/);
        if (!id) break;
        const base = 'https://openapi.youku.com/v2/', params = 'client_id=53e6cc67237fc59a&package=com.huawei.hwvplayer.youku&ext=show&';
        const video = await get(`${base}videos/show.json?${params}video_id=${encodeURIComponent(id)}`);
        if (video?.error || !video?.show?.id) break;
        const show = await get(`${base}shows/show.json?${params}show_id=${encodeURIComponent(video.show.id)}`);
        result = { title: show.name || video.show.name || video.title, episodeTitle: video.title,
          posterUrl: show.poster_large || show.poster || video.bigThumbnail || video.thumbnail, year: year(show.released || show.releasedate_mainland) };
        result.episodes = list(await episodesFor(video.show.id)).map((e, i) => episode(e.id, e.title,
          e.link || `https://v.youku.com/v_show/id_${e.id}.html`, e.sequence || i + 1));
        break;
      }
      case 'imgo': {
        const ids = u.pathname.match(/\/b\/(\d+)\/(\d+)\.html/);
        if (!ids) break;
        const info = (await get(`https://pcweb.api.mgtv.com/video/info?cid=${ids[1]}&vid=${ids[2]}`))?.data?.info;
        if (info) result = { title: info.title, episodeTitle: info.videoName, posterUrl: info.clipImage2 || info.clipImage || info.videoImage, year: year(info.detail?.releaseTime) };
        result.episodes = list(await episodesFor(ids[1])).map((e, i) => episode(e.video_id, [e.t2, e.t1].filter(Boolean).join(' '),
          `https://www.mgtv.com/b/${ids[1]}/${e.video_id}.html`, i + 1));
        break;
      }
      case 'migu': {
        const id = q('cid');
        if (!id) break;
        const data = (await get(`https://v3-sc.miguvideo.com/program/v4/cont/content-info/${id}/1`))?.body?.data;
        if (!data) break;
        let series = data;
        // epsID is the parent returned by this exact video, never a title-search guess.
        if (!list(data.datas).length && data.epsID && text(data.epsID) !== id) {
          try { series = (await get(`https://v3-sc.miguvideo.com/program/v4/cont/content-info/${encodeURIComponent(data.epsID)}/1`))?.body?.data || data; }
          catch { /* Retain the current video if its parent is unavailable. */ }
        }
        result = { title: series.name || data.name, posterUrl: series.h5pics?.highResolutionV || series.pics?.highResolutionV || data.h5pics?.highResolutionV || data.pics?.highResolutionV || data.h5pics?.highResolutionH, year: year(series.year || data.year || data.publishTime) };
        result.episodes = list(series.datas).map((e, i) => episode(e.pID, e.name,
          `https://www.miguvideo.com/mgs/website/prd/detail.html?cid=${e.pID}`, i + 1));
        break;
      }
      case 'sohu': {
        result = await page();
        const state = objectState(html, 'videoData');
        const playlist = state.match(/\b(?:plid|sid)["']?\s*:\s*["']?(\d+)/)?.[1];
        if (!playlist) break;
        const data = source?.getPlaylistData ? await source.getPlaylistData(playlist) : null;
        if (data) {
          result.title = data.albumName || data.album_name || result.title;
          result.posterUrl = data.largeVerPicUrl || data.albumPicUrl || result.posterUrl;
          result.year = year(data.publishYear) || result.year;
        }
        result.episodes = list(await episodesFor(playlist)).map((e, i) => episode(e.vid, e.title, e.url, i + 1));
        break;
      }
      case 'leshi': {
        result = await page();
        const info = objectState(html, '__INFO__');
        const videoId = match(/vplay\/(\d+)/);
        if (!videoId || !info || !(new RegExp('\\b(?:vid|id)["\\\']?\\s*:\\s*["\\\']?' + videoId + '\\b')).test(info)) break;
        result.title = field(info, 'pTitle') || result.title;
        result.posterUrl = field(info, 'pPicShutu') || field(info, 'pPic') || result.posterUrl;
        const pid = info.match(/\bpid["']?\s*:\s*["']?(\d+)/)?.[1];
        result.episodes = list(await episodesFor(pid)).map((e, i) => episode(e.vid || e.id, e.title || e.name, e.url || e.link, i + 1));
        break;
      }
      case 'xigua': {
        result = await page();
        const id = match(/(\d{10,})/);
        result.episodes = list(await episodesFor(id)).map((e, i) => episode(e.gid, e.title, e.url, e.seq_num || i + 1));
        break;
      }
      case 'maiduidui': {
        result = await page();
        const id = match(/\/video\/([a-f\d]+)\.html/i);
        result.episodes = list(await episodesFor(id)).map((e, i) => episode(e.episodeId, e.title,
          `https://www.mddcloud.com.cn/video/${id}.html?uuid=${encodeURIComponent(e.episodeId)}`, i + 1));
        break;
      }
      case 'aiyifan': {
        result = await page();
        const id = match(/\/play\/([^/]+)/);
        result.episodes = list(await episodesFor(id)).map((e, i) => episode(e.id, e.title, e.link, i + 1));
        break;
      }
      case 'hongguo': {
        const ids = u.pathname.match(/\/player\/(\d+)\/(\d+)/);
        if (!ids) break;
        const data = await episodesFor(ids[1]);
        result = { posterUrl: data.imageUrl, year: data.year,
          episodes: list(data.episodes).map(e => episode(e.vid, e.title || `第${e.index}集`,
            `https://www.hongguoduanju.com/player/${ids[1]}/${e.vid}`, e.index)) };
        break;
      }
      case 'bahamut': {
        const data = await episodesFor(q('sn'));
        if (!data?.anime || !data?.video || text(data.video.videoSn) !== q('sn')) break;
        const a = data.anime;
        result = { title: a.title, posterUrl: a.cover || data.video.cover, year: year(data.video.seasonStart || data.video.upTime),
          episodes: Object.entries(a.episodes || {}).flatMap(([group, entries]) => list(entries).map((e, i) => episode(e.videoSn,
            `${a.title} 第${e.episode}集`, `https://ani.gamer.com.tw/animeVideo.php?sn=${e.videoSn}`, Number(e.episode) || i + 1,
            { label: `第${e.episode}集`, group: group === '0' ? '' : '特别篇' }))) };
        break;
      }
      case 'animeko': {
        const epId = match(/\/ep\/(\d+)/);
        if (!epId) break;
        const detail = await get(`https://api.bgm.tv/v0/episodes/${epId}`);
        if (text(detail?.id) !== epId || !detail.subject_id) break;
        const subject = await get(`https://api.bgm.tv/v0/subjects/${detail.subject_id}`);
        result = { title: subject.name_cn || subject.name, posterUrl: subject.images?.large || subject.images?.common, year: year(subject.date),
          episodes: list(await episodesFor(text(detail.subject_id))).map((e, i) => episode(e.id, e.name_cn || e.name || `第${e.sort}集`,
            `https://bgm.tv/ep/${e.id}`, e.sort || i + 1, { label: `第${e.sort}集`, group: e.type === 0 ? '' : '特别篇' })) };
        break;
      }
    }
    if (!result.title || !result.posterUrl) {
      try {
        const fallback = await page();
        result = { ...fallback, ...Object.fromEntries(Object.entries(result).filter(([, v]) => v !== '' && v != null)) };
      } catch { /* Keep successfully resolved fields and episodes. */ }
    }
    return finish(result, inputUrl, url, platform);
  });
}

/** Extend an already authorized URL search; never bypass the loaded core's access checks. */
function wrapUrlMetadata(handler, directory, dependencies) {
  let corePromise;
  return async function handleUrlMetadata(request, ...args) {
    const url = new URL(request.url);
    const requested = request.method === 'GET' && /(?:^|\/)api\/v2\/search\/anime\/?$/.test(url.pathname) && url.searchParams.get('app_url_metadata') === '1';
    const response = await handler(request, ...args);
    if (!requested || !response.ok || !String(response.headers.get('content-type')).includes('application/json')) return response;
    let body;
    try { body = await response.clone().json(); } catch { return response; }
    if (body.success !== true || !Array.isArray(body.animes)) return response;
    const input = url.searchParams.get('keyword') || '';
    if (!platformFor(input)) return response;
    let metadata = null, timeout;
    try {
      const deps = dependencies || await (corePromise ||= loadCore(directory));
      metadata = await Promise.race([
        resolveUrlMetadata(input, deps),
        new Promise(resolve => { timeout = setTimeout(() => resolve(null), 20000); })
      ]);
    } catch { /* Missing/older source APIs fall back to the native App resolver. */ }
    finally { clearTimeout(timeout); }
    const headers = new Headers(response.headers);
    headers.delete('content-length'); headers.delete('content-encoding'); headers.set('cache-control', 'no-store');
    return new Response(JSON.stringify({ ...body, appUrlMetadata: metadata }), { status: response.status, headers });
  };
}

module.exports = { wrapUrlMetadata, resolveUrlMetadata, platformFor, pageMeta, objectState, identity, finish };
