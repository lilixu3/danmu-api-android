'use strict';
const test = require('node:test');
const assert = require('node:assert/strict');
const { resolveUrlMetadata, wrapUrlMetadata, platformFor, pageMeta, finish } = require('../app/src/main/assets/nodejs-project/app-url-metadata.js');
const bv = 'BV11xX5BjECL';
const base = `https://www.bilibili.com/video/${bv}`;
const video = { code:0, data:{ bvid:bv, aid:123, title:'康斯坦丁', pic:'https://i0.hdslb.com/correct.jpg',
  pages:[{cid:10,page:1,part:'上部'}, {cid:20,page:2,part:'下部'}],
  ugc_season:{title:'其他电影合集', sections:[{episodes:[{id:30,title:'别的电影',pic:'https://wrong/cover.jpg'}]}]} } };
const deps = (data, source = {}) => ({get:async () => structuredClone(data), source:async () => source});

test('Bilibili short URL resolves to exact video cover and selected P without importing UGC collections', async () => {
  const d = deps(video, {resolveB23Link:async () => base+'?p=2&share_source=copy'});
  const short = await resolveUrlMetadata('https://b23.tv/NZWHH6K', d);
  const long = await resolveUrlMetadata(base+'?p=2', d);
  assert.equal(short.posterUrl, 'https://i0.hdslb.com/correct.jpg');
  assert.equal(short.title, '康斯坦丁');
  assert.deepEqual(short.episodes, long.episodes);
  assert.equal(short.episodes.length, 2);
  assert.equal(short.selectedEpisodeId, '20');
  assert.equal(short.canonicalUrl, base+'?p=2');
  assert.equal(short.episodeTitle, '下部');
  assert.equal(short.episodesComplete, true);
});

test('invalid Bilibili P and absent PGC episode never default to the first episode', async () => {
  for (const page of ['9','0','bad']) {
    const r = await resolveUrlMetadata(base+'?p='+page, deps(video));
    assert.equal(r.selectedEpisodeId, ''); assert.equal(r.episodes.length, 0);
    assert.equal(r.canonicalUrl, base+'?p='+page); assert.equal(r.episodesComplete, false);
  }
  const r = await resolveUrlMetadata('https://www.bilibili.com/bangumi/play/ep99', deps({code:0,result:{title:'剧名',cover:'https://i0.hdslb.com/series.jpg',episodes:[{id:1,title:'1'}]}}));
  assert.equal(r.episodes.length, 0); assert.equal(r.selectedEpisodeId, '');
  assert.match(r.notice, /未找到/);
});

test('PGC regular episodes and specials retain identity and section, season URL requires choosing an episode', async () => {
  const data = {code:0,result:{title:'剧名',cover:'https://i0.hdslb.com/series.jpg',episodes:[{id:1,title:'1'}],section:[{title:'特别篇',episodes:[{id:2,title:'SP',long_title:'花絮'}]}]}};
  const selected = await resolveUrlMetadata('https://www.bilibili.com/bangumi/play/ep2', deps(data));
  assert.equal(selected.episodes[1].group, '特别篇'); assert.equal(selected.selectedEpisodeId, '2');
  const series = await resolveUrlMetadata('https://www.bilibili.com/bangumi/play/ss10', deps(data));
  assert.equal(series.episodes.length, 2); assert.equal(series.selectedEpisodeId, '');
});

test('wrong API video identity and hostile lookalike domains are not trusted', async () => {
  const bad = structuredClone(video); bad.data.bvid = 'BVwrong';
  const r = await resolveUrlMetadata(base, deps(bad));
  assert.equal(r.posterUrl, ''); assert.equal(r.episodes.length, 0);
  for (const url of ['https://bilibili.com.evil.test/video/BVabc','https://evil.test/?url=https://www.bilibili.com/video/BVabc','http://127.0.0.1/video/BVabc']) assert.equal(platformFor(url), null);
});

test('page metadata ignores recommendation titles pictures and years', () => {
  const html = `<title>正确视频</title><meta property="og:image" content="https://site.test/current.jpg"><script>{"title":"广告","pic":"https://ad.test/a.jpg","year":"1990","episode":"9"}</script>`;
  assert.deepEqual(pageMeta(html, 'https://www.iqiyi.com/v_correct.html'), {title:'正确视频',posterUrl:'https://site.test/current.jpg',year:''});
  assert.deepEqual(pageMeta('<meta property="og:url" content="https://www.iqiyi.com/v_other.html">'+html,'https://www.iqiyi.com/v_correct.html'), {});
});

const examples = [
  {key:'tencent',url:'https://v.qq.com/x/cover/series/ep2.html', id:'series', rows:[{vid:'ep1',title:'第一集'},{vid:'ep2',title:'第二集'}]},
  {key:'iqiyi',url:'https://www.iqiyi.com/v_two.html',id:'two',rows:[{id:'one',title:'第一集',link:'https://www.iqiyi.com/v_one.html'},{id:'two',title:'第二集',link:'https://www.iqiyi.com/v_two.html'}]},
  {key:'youku',url:'https://v.youku.com/v_show/id_two.html',id:'show',rows:[{id:'one',title:'第一集'},{id:'two',title:'第二集'}]},
  {key:'imgo',url:'https://www.mgtv.com/b/123/2.html',id:'123',rows:[{video_id:'1',t2:'第1集'},{video_id:'2',t2:'第2集'}]},
  {key:'sohu',url:'https://tv.sohu.com/v/two.html',id:'123',rows:[{vid:'1',title:'第1集',url:'https://tv.sohu.com/v/one.html'},{vid:'2',title:'第2集',url:'https://tv.sohu.com/v/two.html'}]},
  {key:'leshi',url:'https://www.le.com/ptv/vplay/2.html',id:'123',rows:[{vid:'1',title:'第1集',url:'https://www.le.com/ptv/vplay/1.html'},{vid:'2',title:'第2集',url:'https://www.le.com/ptv/vplay/2.html'}]},
  {key:'xigua',url:'https://www.douyin.com/lvdetail/12345678902',id:'12345678902',rows:[{gid:'12345678901',title:'第1集',url:'https://m.ixigua.com/video/12345678901'},{gid:'12345678902',title:'第2集',url:'https://m.ixigua.com/video/12345678902'}]},
  {key:'maiduidui',url:'https://www.mddcloud.com.cn/video/abcdef.html?uuid=two',id:'abcdef',rows:[{episodeId:'one',title:'第1集'},{episodeId:'two',title:'第2集'}]},
  {key:'aiyifan',url:'https://www.yfsp.tv/play/show?id=two',id:'show',rows:[{id:'one',title:'第1集',link:'https://www.yfsp.tv/play/show?id=one&videoId=111'},{id:'two',title:'第2集',link:'https://www.yfsp.tv/play/show?id=two&videoId=222'}]},
  {key:'hongguo',url:'https://www.hongguoduanju.com/player/123/2',id:'123',rows:{imageUrl:'https://site.test/series.jpg',episodes:[{vid:'1',index:1},{vid:'2',index:2}]}},
  {key:'bahamut',url:'https://ani.gamer.com.tw/animeVideo.php?sn=2',id:'2',rows:{video:{videoSn:2},anime:{title:'剧名',cover:'https://site.test/series.jpg',episodes:{0:[{videoSn:1,episode:1},{videoSn:2,episode:2}]}}}},
  {key:'animeko',url:'https://bgm.tv/ep/2',id:'123',rows:[{id:1,name_cn:'第1集',sort:1,type:0},{id:2,name_cn:'第2集',sort:2,type:0}]}
];
for (const sample of examples) test(`${sample.key} maps source episodes to distinct playback links and selects the original episode`, async () => {
  let called = false;
  const source = {getEpisodes:async id => { assert.equal(id, sample.id); called = true; return sample.rows; },
    _videoIdToEntityId:() => '123', _fetchBaseInfoData:async () => ({data:{base_data:{title:'剧名',image_url:'https://site.test/series.jpg'}}}),
    getPlaylistData:async () => ({albumName:'剧名',largeVerPicUrl:'https://site.test/series.jpg'})};
  const d = {source:async () => source, get:async url => {
    if (url.includes('/videos/show.json')) return {title:'第二集',show:{id:'show'}};
    if (url.includes('/shows/show.json')) return {name:'剧名',poster:'https://site.test/series.jpg'};
    if (url.includes('pcweb.api.mgtv.com')) return {data:{info:{title:'剧名',clipImage:'https://site.test/series.jpg'}}};
    if (url.includes('/v0/episodes/')) return {id:2,subject_id:123};
    if (url.includes('/v0/subjects/')) return {name_cn:'剧名',images:{large:'https://site.test/series.jpg'}};
    return `<title>剧名</title><meta property="og:image" content="https://site.test/series.jpg"><script>var videoData={plid:123};window.__INFO__={video:{vid:2,pid:123,pTitle:"剧名"}};</script>`;
  }};
  const r = await resolveUrlMetadata(sample.url,d);
  assert.equal(called,true); assert.equal(r.episodes.length,2,JSON.stringify(r));
  assert.equal(r.selectedEpisodeId,r.episodes[1].id); assert.equal(r.canonicalUrl,r.episodes[1].url);
  assert.equal(r.posterUrl,'https://site.test/series.jpg');
  assert.equal(r.episodesComplete,false); // Legacy source methods do not prove pagination completeness.
});

test('Migu uses the current content response and selects the requested cid', async () => {
  const r = await resolveUrlMetadata('https://www.miguvideo.com/mgs/website/prd/detail.html?cid=2', deps({body:{data:{name:'剧名',h5pics:{highResolutionV:'https://site.test/series.jpg'},datas:[{pID:1,name:'第一集'},{pID:2,name:'第二集'}]}}}));
  assert.equal(r.selectedEpisodeId,'2'); assert.equal(r.episodes.length,2);
});

test('partial or empty episode lists retain the exact input link and never claim completion', () => {
  const r = finish({title:'剧名',episodes:[{id:'1',title:'第一集',url:'https://www.iqiyi.com/v_one.html'}]}, 'input','https://www.iqiyi.com/v_two.html',platformFor('https://www.iqiyi.com/v_two.html'));
  assert.equal(r.episodes.length,0); assert.equal(r.episodesComplete,false); assert.match(r.notice,/未找到/);
});

const request = () => new Request('http://localhost/user/api/v2/search/anime?app_url_metadata=1&keyword='+encodeURIComponent(base));
const accepted = () => Response.json({success:true,animes:[{animeId:0}]});
test('extension requires a successful authorized core response and leaves ordinary requests untouched', async () => {
  let loads=0;
  const d = {get:async()=>{loads++;return video;},source:async()=>({})};
  for (const status of [401,403,429]) {
    const r=await wrapUrlMetadata(async()=>Response.json({success:false},{status}),'missing',d)(request());
    assert.equal(r.status,status);
  }
  const rejected=await wrapUrlMetadata(async()=>Response.json({success:false,animes:[]}), 'missing',d)(request());
  assert.equal((await rejected.json()).appUrlMetadata,undefined); assert.equal(loads,0);
  const response=accepted();
  assert.equal(await wrapUrlMetadata(async()=>response,'missing',d)(new Request('http://localhost/api/v2/search/anime?keyword=test')),response);
  const result=await wrapUrlMetadata(accepted,'missing',d)(request());
  assert.equal((await result.json()).appUrlMetadata.title,'康斯坦丁');
  assert.equal(result.headers.get('cache-control'),'no-store');
});

test('missing core capabilities preserve the core response for native fallback', async () => {
  const r=await wrapUrlMetadata(accepted,'/missing-core')(request());
  const b=await r.json(); assert.equal(b.success,true); assert.equal(b.appUrlMetadata,null);
});

test('Migu follows only the epsID supplied by the requested video to obtain the series list', async () => {
  const requested=[];
  const r=await resolveUrlMetadata('https://www.miguvideo.com/mgs/website/prd/detail.html?cid=2',{
    source:async()=>({}),get:async url=>{
      requested.push(url);
      return url.includes('/2/1') ? {body:{data:{name:'当前集',epsID:'100'}}} :
        {body:{data:{name:'剧名',h5pics:{highResolutionV:'https://site.test/series.jpg'},datas:[{pID:1,name:'第一集'},{pID:2,name:'第二集'}]}}};
    }
  });
  assert.equal(requested.length,2);assert.ok(requested[1].includes('/100/1'));
  assert.equal(r.title,'剧名');assert.equal(r.selectedEpisodeId,'2');assert.equal(r.episodes.length,2);
});

test('episode provider failure retains correct title and poster and leaves the original URL usable',async()=>{
  const r=await resolveUrlMetadata('https://v.youku.com/v_show/id_two.html',{
    source:async()=>({getEpisodes:async()=>{throw new Error('temporarily unavailable');}}),
    get:async url=>url.includes('/videos/show.json') ? {title:'第二集',show:{id:'show'}} : {name:'剧名',poster:'https://site.test/series.jpg'}
  });
  assert.equal(r.title,'剧名');assert.equal(r.posterUrl,'https://site.test/series.jpg');
  assert.equal(r.episodes.length,0);assert.equal(r.canonicalUrl,'https://v.youku.com/v_show/id_two.html');
});

test('Tencent cover-only URL keeps the series list without choosing episode one',async()=>{
  const r=await resolveUrlMetadata('https://v.qq.com/x/cover/series.html',{
    source:async()=>({getEpisodes:async()=>[{vid:'one',title:'第一集'},{vid:'two',title:'第二集'}]}),
    get:async()=>'<title>剧名</title><meta property="og:image" content="https://site.test/series.jpg">'
  });
  assert.equal(r.episodes.length,2);assert.equal(r.selectedEpisodeId,'');
});

test('UGC season tracking parameter cannot turn a video URL into an unrelated PGC series',async()=>{
  const requested=[];
  const r=await resolveUrlMetadata(base+'?season_id=6674023',{
    source:async()=>({}),get:async url=>{requested.push(url);return video;}
  });
  assert.ok(requested[0].includes('/x/web-interface/view'));
  assert.equal(r.title,'康斯坦丁');assert.equal(r.episodes.length,2);
});
