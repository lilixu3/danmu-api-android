const test=require('node:test');
const assert=require('node:assert/strict');
const fs=require('node:fs'),os=require('node:os'),path=require('node:path');
const {catalog,probeSource,targetFor}=require('../app/src/main/assets/nodejs-project/app-source-probe.js');
const {bindCoreSourceContext}=require('../app/src/main/assets/nodejs-project/app-proxy-routing.js');
const coreResolver=`init(env){this.env=env;},makeProxyUrl(target){
 const entries=String(this.env.PROXY_URL || '').split(',').map(x=>x.trim());
 const specific=entries.find(x=>x.startsWith('aiyifan@') || x.startsWith('tmdb@'));
 if(specific){const base=new URL(specific.slice(specific.indexOf('@')+1)),url=new URL(target);url.protocol=base.protocol;url.host=base.host;url.pathname=base.pathname.replace(/\\/$/,'')+url.pathname;return url.href;}
 const universal=entries.find(x=>x.startsWith('@'));if(universal)return universal.slice(1).replace(/\\/$/,'')+'/'+target;
 return target;
}`;
async function fixture(action,resolver=coreResolver) {
 const directory=fs.mkdtempSync(path.join(os.tmpdir(),'source-probe-'));
 fs.mkdirSync(path.join(directory,'configs'));fs.mkdirSync(path.join(directory,'sources'));fs.mkdirSync(path.join(directory,'utils'));
 fs.writeFileSync(path.join(directory,'package.json'),'{"type":"module"}');
 fs.writeFileSync(path.join(directory,'configs/globals.js'),'export const Globals={'+resolver+'};');
 fs.writeFileSync(path.join(directory,'sources/aiyifan.js'),`export default class AiyifanSource {constructor(){this.DETAILS_API = AIYIFAN_APP_BASE_URL + "api/Video/VideoDetails";}}`);
 fs.writeFileSync(path.join(directory,'utils/aiyifan-util.js'),`export const AIYIFAN_APP_BASE_URL = "https://api.tripdata.app/";`);
 fs.writeFileSync(path.join(directory,'configs/envs.js'),"static ALLOWED_SOURCES=['aiyifan','douban','local','newsource','tmdb','custom','other','vod'];");
 fs.writeFileSync(path.join(directory,'sources/newsource.js'),"const url='https://api.new-source.test/path';");
 try {await bindCoreSourceContext(directory);await action(directory);}finally{await bindCoreSourceContext('');fs.rmSync(directory,{recursive:true,force:true});}
}
test('catalog follows current enabled configuration and discovers added sources',()=>fixture(async directory=>{
 assert.deepEqual(catalog(directory,{SOURCE_ORDER:'aiyifan,local'}).enabled,['aiyifan','local']);
 assert.equal(targetFor('newsource',directory,{}),'https://api.new-source.test');
 const result=await probeSource('local',{directory,env:{},snapshot:{},signal:new AbortController().signal});assert.equal(result.route,'本地文件');
}));
test('anonymous Douban and App node attribution are independent of enhanced-direct toggle',()=>fixture(async directory=>{
 const seen=[];
 const result=await probeSource('douban',{directory,env:{},snapshot:{config:{enabled:false,proxy:{enabled:true,rules:{douban:'node:one'},nodes:[{id:'one',name:'节点一'}]}}},signal:new AbortController().signal,
 fetchImpl:async(url,init)=>{seen.push({url,headers:init.headers});if(url.includes('/geoip'))return Response.json({country_code:'HK'});return Response.json({id:'36296618',title:'test'});}});
 assert.equal(result.routeType,'app');assert.match(result.route,/节点一/);assert.equal(result.connected,true);assert.equal(seen[0].headers.Cookie,undefined);
 // Node geo request deliberately uses its own verified proxy transport, not a fake location.
 assert.equal(result.region,'未确认');assert.equal(seen.length,1);
}));
test('HTTP region block is a reachable but restricted source, not a transport failure',()=>fixture(async directory=>{
 const result=await probeSource('aiyifan',{directory,env:{SOURCE_ORDER:'aiyifan'},snapshot:{},signal:new AbortController().signal,
 fetchImpl:async url=>url.includes('/geoip')?Response.json({country_code:'CN'}):new Response('Error 1009 Access Denied',{status:403})});
 assert.equal(result.connected,true);assert.equal(result.status,'restricted');assert.equal(result.region,'未确认');assert.match(result.reason,/地区/);
}));

const {routeFor}=require('../app/src/main/assets/nodejs-project/app-proxy-routing.js');
function trace(host,loc='HK',ip='203.0.113.8') {return `fl=abc\nh=${host}\nip=${ip}\nloc=${loc}\ntls=TLSv1.3\ncolo=LAX\n`;}
test('exit region uses the same source domain and route; colo is not the exit location',()=>fixture(async directory=>{
 const seen=[];
 const result=await probeSource('douban',{directory,env:{},snapshot:{config:{enabled:true,sources:['douban'],proxy:{enabled:true,rules:{douban:'inherit'}}}},signal:new AbortController().signal,
 fetchImpl:async(url,init)=>{seen.push({url,route:routeFor(new URL(url),{}).mode,redirect:init.redirect});
  return url.endsWith('/cdn-cgi/trace')?new Response(trace('m.douban.com')):new Response('{}',{headers:{'cf-ray':'fixture'}});}});
 assert.equal(result.region,'香港');assert.equal(result.regionEvidence,'same-domain');assert.equal(result.regionHost,'m.douban.com');
 assert.equal(seen.length,2);assert.equal(seen[1].url,'https://m.douban.com/cdn-cgi/trace');assert.equal(seen[1].route,'inherit');assert.equal(seen[1].redirect,'manual');
}));
for(const [name,body,status] of [
 ['wrong domain',trace('some-other.test'),200],['bad IP',trace('m.douban.com','HK','not-an-ip'),200],
 ['HTML challenge','<html>loc=HK</html>',200],['redirect',trace('m.douban.com'),302],
 ['duplicate field',trace('m.douban.com')+'loc=US\n',200],['oversized',trace('m.douban.com')+'x'.repeat(9000),200],
 ['unknown country',trace('m.douban.com','XX'),200]
])test('does not claim exit region from '+name,()=>fixture(async directory=>{
 const result=await probeSource('douban',{directory,env:{},snapshot:{},signal:new AbortController().signal,
 fetchImpl:async url=>url.endsWith('/cdn-cgi/trace')?new Response(body,{status}):new Response('{}',{headers:{server:'cloudflare'}})});
 assert.equal(result.region,'未确认');assert.equal(result.regionEvidence,'unknown');assert.equal(result.connected,true);
}));
test('each probe samples its own domain route and never reuses a global egress cache',()=>fixture(async directory=>{
 let calls=0;
 const opts={directory,env:{},snapshot:{},signal:new AbortController().signal,fetchImpl:async url=>url.endsWith('/cdn-cgi/trace')?new Response(trace('m.douban.com',++calls===1?'HK':'US')):new Response('{}',{headers:{'cf-ray':'fixture'}})};
 assert.equal((await probeSource('douban',opts)).region,'香港');assert.equal((await probeSource('douban',opts)).region,'美国');assert.equal(calls,2);
}));
test('a reverse proxy cannot substantiate the source exit location',()=>fixture(async directory=>{
 const seen=[];
 const result=await probeSource('aiyifan',{directory,env:{PROXY_URL:'@https://reverse.test'},snapshot:{},signal:new AbortController().signal,
 fetchImpl:async url=>{seen.push(url);return new Response('{}',{headers:{server:'cloudflare'}});}});
 assert.equal(result.routeType,'core');assert.equal(result.region,'未确认');assert.equal(seen.length,1);
}));


test('TMDB without a configured key reports a response and missing configuration on 401',()=>fixture(async directory=>{
 const result=await probeSource('tmdb',{directory,env:{TMDB_API_KEY:'  '},snapshot:{},signal:new AbortController().signal,
  fetchImpl:async url=>{assert.equal(new URL(url).searchParams.has('api_key'),false);return Response.json({status_code:7},{status:401});}});
 assert.equal(result.status,'ok');assert.equal(result.connected,true);assert.equal(result.configurationRequired,true);
 assert.equal(result.httpStatus,401);assert.match(result.reason,/已响应.*API Key 未配置/);
}));

for(const selection of ['inherit','node:fixture'])test('TMDB uses the configured API key with '+selection+' and does not disclose it',()=>fixture(async directory=>{
 const key='test-key&value=#?';const seen=[];
 const result=await probeSource('tmdb',{directory,env:{TMDB_API_KEY:key},
  snapshot:{config:{enabled:false,proxy:{enabled:true,rules:{tmdb:selection},nodes:[{id:'fixture',name:'fixture'}]}}},signal:new AbortController().signal,
  fetchImpl:async url=>{const target=new URL(url);seen.push(target);
   assert.equal(routeFor(target,{}).mode,selection==='inherit'?'inherit':'proxy');
   if(target.pathname==='/cdn-cgi/trace'){assert.equal(target.search,'');return new Response(trace('api.tmdb.org'));}
   assert.equal(target.searchParams.get('api_key'),key);
   return Response.json({images:{}},{headers:{server:'cloudflare'}});
  }});
 assert.equal(result.status,'ok');assert.equal(result.connected,true);assert.notEqual(result.configurationRequired,true);
 assert.equal(seen.length,2);assert.equal(result.region,'香港');assert(!JSON.stringify(result).includes(key));
}));

test('TMDB legacy proxy target includes the configured API key',()=>fixture(async directory=>{
 const key='fixture-key';
 const result=await probeSource('tmdb',{directory,env:{TMDB_API_KEY:key,PROXY_URL:'@https://reverse.test'},snapshot:{},signal:new AbortController().signal,
  fetchImpl:async url=>{assert.equal(url,'https://reverse.test/https://api.tmdb.org/3/configuration?api_key='+key);return Response.json({images:{}});}});
 assert.equal(result.status,'ok');assert.equal(result.routeType,'core');assert(!JSON.stringify(result).includes(key));
}));

test('TMDB configured but rejected key is not mislabeled as missing',()=>fixture(async directory=>{
 const result=await probeSource('tmdb',{directory,env:{TMDB_API_KEY:'invalid-fixture'},snapshot:{},signal:new AbortController().signal,
  fetchImpl:async()=>Response.json({status_code:7},{status:401})});
 assert.equal(result.connected,true);assert.equal(result.status,'restricted');assert.equal(result.configurationRequired,false);
 assert.match(result.reason,/API Key 无效或无权限/);assert(!JSON.stringify(result).includes('invalid-fixture'));
}));

test('TMDB without a key cannot claim a response when the transport failed',()=>fixture(async directory=>{
 const result=await probeSource('tmdb',{directory,env:{},snapshot:{},signal:new AbortController().signal,
  fetchImpl:async()=>{throw new Error('connection refused');}});
 assert.equal(result.connected,false);assert.equal(result.status,'failed');assert.notEqual(result.configurationRequired,true);
}));

for(const [source,env] of [['custom',{}],['custom',{CUSTOM_SOURCE_API_URL:'  '}],['custom',{CUSTOM_SOURCE_API_URL:'not a URL'}],['other',{OTHER_SERVER:''}],['vod',{VOD_SERVERS:''}],['vod',{VOD_SERVERS:'name@'}]]) {
 test('unconfigured '+source+' is not a transport failure: '+JSON.stringify(env),()=>fixture(async directory=>{
  const result=await probeSource(source,{directory,env,snapshot:{},signal:new AbortController().signal,fetchImpl:async()=>assert.fail('must not request an unconfigured source')});
  assert.equal(result.connected,false);assert.equal(result.status,'unconfigured');assert.equal(result.configurationRequired,true);
  assert.equal(result.durationMs,undefined);
 }));
}

test('source endpoints preserve core defaults but support an explicit empty setting and bare VOD URLs',()=>fixture(async directory=>{
 assert.equal(targetFor('other',directory,{}),'https://api.danmu.icu');
 assert.equal(targetFor('other',directory,{OTHER_SERVER:''}),'');
 assert.equal(targetFor('vod',directory,{}),'https://zy.jinchancaiji.com');
 assert.equal(targetFor('vod',directory,{VOD_SERVERS:' https://vod.example/api '}),'https://vod.example/api');
 assert.equal(targetFor('vod',directory,{VOD_SERVERS:', ,empty@, first@https://first.example,second@https://second.example'}),'https://first.example');
 const result=await probeSource('custom',{directory,env:{CUSTOM_SOURCE_API_URL:'https://custom.example/api'},snapshot:{},signal:new AbortController().signal,
  fetchImpl:async url=>{assert.equal(url,'https://custom.example/api');return Response.json({ok:true});}});
 assert.equal(result.status,'ok');assert.equal(result.connected,true);assert.notEqual(result.configurationRequired,true);
}));

for(const selection of ['inherit','direct','node:one'])test('aiyifan dedicated reverse proxy respects App priority: '+selection,()=>fixture(async directory=>{
 const result=await probeSource('aiyifan',{directory,env:{PROXY_URL:'aiyifan@https://reverse.test/prefix'},
  snapshot:{config:{proxy:{enabled:true,rules:{aiyifan:selection},nodes:[{id:'one',name:'App node'}]}}},signal:new AbortController().signal,
  fetchImpl:async url=>{assert.equal(new URL(url).hostname,selection==='node:one'?'api.tripdata.app':'reverse.test');
   if(selection!=='node:one')assert(new URL(url).pathname.startsWith('/prefix/api/'));return Response.json({ret:200});}});
 assert.equal(result.routeType,selection==='node:one'?'app':'core');
}));

test('an explicit App node is not blocked by a malformed core reverse proxy',()=>fixture(async directory=>{
 const result=await probeSource('aiyifan',{directory,env:{PROXY_URL:'aiyifan@invalid core URL'},
  snapshot:{config:{proxy:{enabled:true,rules:{aiyifan:'node:one'},nodes:[{id:'one',name:'App node'}]}}},signal:new AbortController().signal,
  fetchImpl:async url=>{assert.equal(new URL(url).hostname,'api.tripdata.app');return Response.json({ret:200});}});
 assert.equal(result.status,'ok');assert.equal(result.routeType,'app');
}));

for(const source of ['douban','newsource'])for(const proxy of ['@https://reverse.test','http://user:pass@proxy.test:2080/','douban@https://unused.test']) {
 test('unsupported core source uses device network: '+source+' '+proxy,()=>fixture(async directory=>{
  const target=targetFor(source,directory,{});
  const result=await probeSource(source,{directory,env:{PROXY_URL:proxy},snapshot:{},signal:new AbortController().signal,
   fetchImpl:async url=>{assert.equal(new URL(url).href,new URL(target).href);return Response.json({ok:true});}});
  assert.equal(result.route,'使用设备当前网络');assert.equal(result.routeType,'direct');assert.equal(result.status,'ok');
 }));
}
for(const source of ['bahamut','tmdb','bilibili','animeko','aiyifan'])test('supported core source retains core default label: '+source,()=>fixture(async directory=>{
 const result=await probeSource(source,{directory,env:{SOURCE_ORDER:source},snapshot:{},signal:new AbortController().signal,
  fetchImpl:async()=>Response.json({ok:true})});
 assert.equal(result.route,'沿用核心配置');
}));

for(const [version,resolver,expectedRoute] of [
 ['old',`init(env){this.env=env;},makeProxyUrl(url){return url;}`,'direct'],
 ['new',coreResolver,'core'],
 ['future',`init(env){this.env=env;},makeProxyUrl(url){return 'https://future.test/encoded?target='+encodeURIComponent(url);}`,'core']
])test('probe follows the loaded '+version+' core resolver rather than App proxy assumptions',()=>fixture(async directory=>{
 const result=await probeSource('aiyifan',{directory,env:{PROXY_URL:'aiyifan@https://reverse.test'},snapshot:{},signal:new AbortController().signal,
  fetchImpl:async url=>{const host=new URL(url).hostname;assert.equal(host,version==='old'?'api.tripdata.app':version==='new'?'reverse.test':'future.test');return Response.json({ok:true});}});
 assert.equal(result.connected,true);assert.equal(result.routeType,expectedRoute);
 if(version==='old')assert.match(result.reason,/当前核心未对该地址应用/);
},resolver));
test('unavailable or throwing core resolvers stay unconfirmed without sending a guessed request',()=>fixture(async directory=>{
 for(const resolveTarget of [()=>null,()=>{throw new Error('private URL must not escape');}]) {
  const result=await probeSource('aiyifan',{directory,env:{PROXY_URL:'aiyifan@https://reverse.test'},snapshot:{},signal:new AbortController().signal,
   resolveTarget,fetchImpl:async()=>assert.fail('must not guess a route')});
  assert.equal(result.status,'unsupported');assert.equal(result.route,'未确认');assert(!JSON.stringify(result).includes('private URL'));
 }
}));
test('running core targets stay pinned until reload; offline discovery follows changed declarations',()=>fixture(async directory=>{
 fs.writeFileSync(path.join(directory,'utils/aiyifan-util.js'),`export const AIYIFAN_APP_BASE_URL = "https://api.future.test/";`);
 fs.writeFileSync(path.join(directory,'sources/aiyifan.js'),`this.DETAILS_API = AIYIFAN_APP_BASE_URL + "v2/details";`);
 assert.equal(targetFor('aiyifan',directory,{}),'https://api.tripdata.app/api/Video/VideoDetails');
 await bindCoreSourceContext('');
 assert.equal(targetFor('aiyifan',directory,{}),'https://api.future.test/v2/details');
 fs.writeFileSync(path.join(directory,'sources/aiyifan.js'),'this.endpoint = computeEndpoint();');
 const result=await probeSource('aiyifan',{directory,env:{},snapshot:{},signal:new AbortController().signal,fetchImpl:async()=>assert.fail('must not use an obsolete fallback')});
 assert.equal(result.status,'unsupported');
}));
test('core forward region diagnostics retain the core actual helper port',()=>fixture(async directory=>{
 const urls=[];
 const result=await probeSource('tmdb',{directory,env:{PROXY_URL:'http://proxy.test:2080'},snapshot:{},signal:new AbortController().signal,
  resolveTarget:target=>({url:'http://127.0.0.1:15321/proxy?url='+encodeURIComponent(target),kind:'核心 HTTP 代理',forward:true}),
  fetchImpl:async url=>{urls.push(url);assert.equal(new URL(url).port,'15321');const target=new URL(new URL(url).searchParams.get('url'));
   return target.pathname==='/cdn-cgi/trace'?new Response(trace('api.tmdb.org')):new Response('{}',{headers:{server:'cloudflare'}});}});
 assert.equal(urls.length,2);assert.equal(result.region,'香港');
}));
