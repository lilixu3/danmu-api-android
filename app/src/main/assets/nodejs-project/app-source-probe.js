'use strict';
const fs=require('node:fs');
const path=require('node:path');
const {routeFor,withSource,withRoute,resolveCoreTarget,coreProbeTarget,declaredAiyifanTarget,coreProxySources}=require('./app-proxy-routing.js');
const {eligible}=require('./app-outbound-bridge.js');
const {diagnosticRequest,diagnosticTargets,validBilibiliNav}=require('./app-outbound-diagnostics.js');
const fallbackTargets={
 '360':'https://api.so.360kan.com/index?force_v=1&kw=test&pageno=1&v_ap=1&tab=all',
 renren:'https://rrsp.com.cn/',hanjutv:'https://api.xiawen.tv/api/v1/aggregate/search?key=test&scope=101&page=1',
 tencent:'https://v.qq.com/',iqiyi:'https://www.iqiyi.com/',imgo:'https://www.mgtv.com/',bilibili:'https://api.bilibili.com/x/web-interface/nav',
 migu:'https://www.miguvideo.com/',youku:'https://www.youku.com/',sohu:'https://tv.sohu.com/',leshi:'https://so.le.com/',
 xigua:'https://m.ixigua.com/',maiduidui:'https://mob.mddcloud.com.cn/',hongguo:'https://hongguoduanju.com/',
};
function catalog(directory,env) {
 let sources=[];
 try {
  const text=fs.readFileSync(path.join(directory,'configs/envs.js'),'utf8');
  const array=text.match(/(?:static\s+)?ALLOWED_SOURCES\s*=\s*\[([^\]]*)\]/)?.[1] || '';
  sources=[...array.matchAll(/['"]([^'"]+)['"]/g)].map(x=>x[1]);
 } catch {}
 const enabled=String(env.SOURCE_ORDER ?? 'douban,360,renren,hanjutv').split(',').map(x=>x.trim()).filter(Boolean);
 return {sources:[...new Set([...sources,...enabled])],enabled};
}
function targetFor(source,directory,env) {
 if(source==='local')return '';
 if(source==='aiyifan') {
  const running=coreProbeTarget(source,directory);
  if(running!==undefined)return running;
  // Read the installed core's endpoint declarations, without evaluating source code.
  // An unfamiliar layout is unconfirmed instead of silently probing an obsolete API.
  try {
   const content=fs.readFileSync(path.join(directory,'sources/aiyifan.js'),'utf8');
   const assignment=content.match(/\bthis\.DETAILS_API\s*=\s*(?:([A-Z][A-Z0-9_]*)\s*\+\s*)?['"]([^'"\r\n]+)['"]/);
   if(!assignment)return '';
   const constants={};
   if(assignment[1]) {
    const utils=fs.readFileSync(path.join(directory,'utils/aiyifan-util.js'),'utf8');
    const base=utils.match(new RegExp('\\b'+assignment[1]+'\\s*=\\s*[\'"]([^\'"\\r\\n]+)[\'"]'))?.[1];
    if(!base)return '';
    constants[assignment[1]]=base;
   }
   return declaredAiyifanTarget(content,constants);
  }catch{return '';}
 }
 if(diagnosticTargets[source])return diagnosticTargets[source][0];
 if(source==='custom')return String(env.CUSTOM_SOURCE_API_URL ?? '').trim();
 if(source==='other')return String(env.OTHER_SERVER ?? 'https://api.danmu.icu').trim();
 if(source==='vod') {
  // The core accepts both name@URL and bare URLs. An explicit empty value disables all VOD endpoints.
  const entries=String(env.VOD_SERVERS ?? '金蝉@https://zy.jinchancaiji.com').split(',').map(x=>x.trim()).filter(Boolean);
  return entries.map(entry=>entry.includes('@')?entry.slice(entry.indexOf('@')+1).trim():entry).find(Boolean) || '';
 }
 if(fallbackTargets[source])return fallbackTargets[source];
 // New core sources appear automatically. Prefer a literal API origin; never
 // evaluate imported source files merely to discover a probe target.
 try {
  const filename=source==='360'?'kan360':source==='imgo'?'mango':source;
  if(!/^[a-z0-9_-]+$/i.test(filename))return '';
  const content=fs.readFileSync(path.join(directory,'sources',filename+'.js'),'utf8');
  const urls=[...content.matchAll(/https?:\/\/[a-z0-9.-]+(?::\d+)?/ig)].map(m=>m[0]);
  return urls.find(url=>new URL(url).hostname.startsWith('api.')) || urls[0] || '';
 }catch{return '';}
}
// An unrelated IP lookup is not evidence for a domain-based routing rule.
// Only accept a bounded, non-redirected diagnostic response from this origin.
async function domainRegion(target, source, route, legacy, {signal, fetchImpl}) {
 const unknown={region:'未确认',regionEvidence:'unknown',regionNote:'源站没有返回可验证的出口地区；不使用其他网站的 IP 查询结果代替'};
 if(legacy.kind==='核心反向代理')return {...unknown,regionNote:'反向代理的出口无法由客户端确认'};
 const trace=new URL('/cdn-cgi/trace',target);
 const forwardTarget=legacy.forward?new URL(legacy.url):null;
 forwardTarget?.searchParams.set('url',trace.href);
 const traceTarget=forwardTarget?.href || trace.href;
 try {
  const response=await withSource(source,()=>withRoute(legacy.forward?{mode:'inherit'}:route,()=>fetchImpl(traceTarget,{
   signal:AbortSignal.any([signal,AbortSignal.timeout(4500)]),redirect:'manual',headers:{'User-Agent':'danmu-api-android/source-probe'}
  })));
  const reader=response.body?.getReader();
  if(!reader)return unknown;
  let text='',bytes=0;
  try {
   if(response.status!==200 || response.redirected)return unknown;
   while(true){const part=await reader.read();if(part.done)break;bytes+=part.value.byteLength;if(bytes>8192)return unknown;text+=Buffer.from(part.value).toString();}
  }finally{await reader.cancel().catch(()=>{});}
  const fields=new Map();
  for(const line of text.trim().split(/\r?\n/)){const m=line.match(/^([a-z_]+)=([^\r\n]*)$/);if(!m || fields.has(m[1]))return unknown;fields.set(m[1],m[2]);}
  if(fields.get('h')?.toLowerCase()!==trace.hostname.toLowerCase() || !require('node:net').isIP(fields.get('ip') || '') || !/^[A-Z]{2}$/.test(fields.get('loc') || '') || !fields.has('tls'))return unknown;
  const code=fields.get('loc');
  if(['XX','T1'].includes(code))return unknown;
  const region=({CN:'中国大陆',HK:'香港',MO:'澳门',TW:'台湾',US:'美国',JP:'日本',SG:'新加坡',KR:'韩国',GB:'英国',DE:'德国'})[code] || code;
  return {region,regionEvidence:'same-domain',regionHost:trace.hostname,
   regionNote:'来自 '+trace.hostname+' 的同域诊断响应，表示该次诊断连接的出口归属地；来源其他域名、按路径或 IP 分流及后续连接可能不同'};
 }catch{return unknown;}
}
function usesCoreProxy(source,target) {
 if(!coreProxySources.has(source))return false;
 if(source==='animeko')return target.hostname==='api.bgm.tv';
 return true;
}
function validProbePayload(source,status,text) {
 if(!diagnosticTargets[source] && !coreProxySources.has(source))return {valid:true};
 let data;
 try {data=JSON.parse(text);}catch{return {valid:false,reason:'接口返回非 JSON 内容，可能是验证码或网页错误页'};}
 if(!data || typeof data!=='object')return {valid:false,reason:'接口响应格式异常'};
 if(status!==200)return {valid:false,reason:'接口返回 HTTP '+status};
 const valid=source==='bahamut' ? Array.isArray(data.anime)
  : source==='bilibili' ? validBilibiliNav(data)
  : source==='animeko' ? Boolean(data.id) || Array.isArray(data.data) || Boolean(data.data?.id)
  : source==='tmdb' ? true
  : source==='dandan' ? Array.isArray(data.animes)
  : source==='douban' ? !data.banned && String(data.id)==='36296618' && typeof data.title==='string' && data.title.trim().length>0
  : true;
 return valid?{valid:true}:{valid:false,reason:'接口响应格式异常或未返回预期数据'};
}
async function responseText(response,limit=32768) {
 const reader=response.body?.getReader();
 if(!reader)return '';
 const chunks=[];let size=0;
 try {
  while(true) {
   const part=await reader.read();if(part.done)break;
   size+=part.value.byteLength;
   if(size>limit)throw new RangeError('探测响应超过读取上限');
   chunks.push(Buffer.from(part.value));
  }
 }finally{await reader.cancel().catch(()=>{});}
 return Buffer.concat(chunks,size).toString();
}
async function responsePrefix(response,limit=4096) {
 const reader=response.body?.getReader();
 if(!reader)return '';
 const chunks=[];let size=0;
 try {
  while(size<limit) {
   const part=await reader.read();if(part.done)break;
   const remaining=limit-size;
   const chunk=Buffer.from(part.value.subarray(0,remaining));
   chunks.push(chunk);size+=chunk.length;
   if(chunk.length<part.value.byteLength)break;
  }
 }finally{await reader.cancel().catch(()=>{});}
 return Buffer.concat(chunks,size).toString();
}
async function probeSource(source,{directory,env,snapshot,signal,fetchImpl=globalThis.fetch,resolveTarget=resolveCoreTarget}) {
 if(!catalog(directory,env).sources.includes(source))throw new Error('未知来源');
 if(source==='local')return {source,status:'local',connected:true,route:'本地文件',region:'本机',reason:'本地弹幕无需联网'};
 const target=targetFor(source,directory,env);
 const configurable=['custom','other','vod'].includes(source);
 const unconfigured={source,status:'unconfigured',connected:false,configurationRequired:true,route:'未检测',region:'未确认',reason:'来源地址未配置，请在配置管理中填写'};
 if(!target)return configurable ? unconfigured : {source,status:'unsupported',connected:false,route:'未检测',region:'未知',reason:'当前来源没有可安全探测的接口地址'};
 let targets=diagnosticTargets[source]?.length?diagnosticTargets[source].slice():[target];
 if(!targets.includes(target) && source==='aiyifan')targets=[target];
 let url;try{url=new URL(target);if(!['http:','https:'].includes(url.protocol) || url.username || url.password)throw new Error();}catch{return {...unconfigured,reason:'来源地址未正确配置，请检查配置管理中的地址'};}
 const route=routeFor(url,snapshot?.config?.proxy,source);
 const started=Date.now();
 const resolvedCore=new Map();
 if(source==='animeko' && route.mode==='inherit' && usesCoreProxy(source,new URL(targets[0]))) {
  const req=diagnosticRequest(source,targets[0],env);
  try {resolvedCore.set(targets[0],resolveTarget(req.url,source,directory,env) || {});}
  catch {if(String(env.PROXY_URL || '').trim())return {source,status:'unsupported',connected:false,route:'未确认',region:'未确认',reason:'当前核心未提供可识别的线路解析，无法确认实际请求线路'};}
  const official=resolvedCore.get(targets[0]);
  if(!official?.kind && targets.length>1)targets=[targets[1],targets[0],...targets.slice(2)];
 }
 let last;
 for(const candidate of targets) {
  signal.throwIfAborted();
  let candidateUrl;try{candidateUrl=new URL(candidate);if(!['http:','https:'].includes(candidateUrl.protocol) || candidateUrl.username || candidateUrl.password)continue;}catch{continue;}
  const req=diagnosticRequest(source,candidate,env);
  let legacy=resolvedCore.get(candidate) || {};
  if(route.mode==='inherit' && usesCoreProxy(source,candidateUrl) && !resolvedCore.has(candidate)) {
   try {legacy=resolveTarget(req.url,source,directory,env) || {};}
   catch {
    if(String(env.PROXY_URL || '').trim())return {source,status:'unsupported',connected:false,route:'未确认',region:'未确认',reason:'当前核心未提供可识别的线路解析，无法确认实际请求线路'};
   }
   if(!legacy.url && String(env.PROXY_URL || '').trim())return {source,status:'unsupported',connected:false,route:'未确认',region:'未确认',reason:'当前核心未提供线路解析，无法确认代理配置是否生效'};
  }
  const appSelected=route.mode==='proxy';
  const useLegacy=route.mode==='inherit' && Boolean(legacy.kind);
  const enhanced=!useLegacy && withSource(source,()=>eligible(candidateUrl,snapshot,env));
  const ignoredProxy=usesCoreProxy(source,candidateUrl) && legacy.url && !legacy.kind && String(env.PROXY_URL || '').trim();
  const routeLabel=appSelected ? 'App 代理 · '+(route.node?.name || '节点已失效')
   : useLegacy ? legacy.kind
   : route.mode==='direct' ? '设备当前网络'
   : enhanced ? '增强直连'
   : ignoredProxy ? '使用设备当前网络（核心配置未应用于此接口）'
   : source==='animeko' && !usesCoreProxy(source,candidateUrl) ? '核心配置备用直连'
   : coreProxySources.has(source) ? '沿用核心配置' : '使用设备当前网络';
  const result={source,route:routeLabel,routeType:appSelected?'app':useLegacy?'core':enhanced?'enhanced':'direct',region:'未确认',regionEvidence:'unknown',probeHost:candidateUrl.hostname,regionNote:'源站未提供可验证的出口地区',status:'failed',connected:false};
  let cloudflare=false;
  try {
   const response=await withSource(source,()=>withRoute(useLegacy?{mode:'inherit'}:route,()=>fetchImpl(useLegacy?legacy.url:req.url,{
    headers:req.headers,signal:AbortSignal.any([signal,AbortSignal.timeout(source==='animeko'?3500:12000)])
   })));
   result.httpStatus=response.status;result.connected=true;
   cloudflare=response.headers.has('cf-ray') || /cloudflare/i.test(response.headers.get('server') || '');
   let text='';
   if((diagnosticTargets[source] || coreProxySources.has(source)) && response.status<400) {
    try {text=await responseText(response);}
    catch {
     result.status='restricted';result.reason='网络已通，但探测响应内容超出读取限制或读取失败';
     result.durationMs=Date.now()-started;
     last={...result,cloudflare,routeMode:route.mode,legacy};
     continue;
    }
   } else if(response.status===403) {
    try {text=await responsePrefix(response);}catch{}
   } else {
    await response.body?.cancel().catch(()=>{});
   }
   const valid=validProbePayload(source,response.status,text);
   if(response.status===403 && /1009|country.*banned|region.*(?:block|restrict)/i.test(text)) {
    result.status='restricted';result.reason='网络已通，源站限制当前出口地区';
   } else if(source==='tmdb' && response.status===401) {
    result.configurationRequired=!String(env.TMDB_API_KEY || '').trim();
    result.status=result.configurationRequired?'ok':'restricted';
    result.reason=result.configurationRequired?'已响应，但 API Key 未配置':'已响应，但 API Key 无效或无权限';
   } else if([401,403,429].includes(response.status)) {
    result.status='restricted';result.reason=response.status===429?'网络已通，接口限流':'网络已通，接口要求认证或触发风控';
   } else if(response.status>=400) {
    result.status='restricted';result.reason='网络已通，探测地址返回 HTTP '+response.status;
   } else if(!valid.valid) {
    result.status='restricted';result.reason='网络已通，'+valid.reason;
   } else {
    result.status='ok';result.reason=diagnosticTargets[source]?'探测接口已响应；不代表搜索、详情和弹幕全部可用':'站点已响应；业务接口仍可能需要认证';
   }
   if(ignoredProxy)result.reason+='；当前核心未对该地址应用已配置的代理';
   result.durationMs=Date.now()-started;
   if(result.status==='ok') {
    if(!signal.aborted && cloudflare)Object.assign(result,await domainRegion(candidateUrl,source,route,useLegacy?legacy:{},{signal,fetchImpl}));
    return result;
   }
    last={...result,cloudflare,routeMode:route.mode,legacy};
  } catch(error) {
   result.durationMs=Date.now()-started;result.connected=false;
   result.reason=appSelected && !route.node?'绑定的代理节点已失效':'连接失败或响应读取失败';
   last={...result,cloudflare:false,routeMode:route.mode,legacy:{}};
  }
 }
 last ||= {source,status:'failed',connected:false,route:'未确认',routeType:'direct',region:'未确认',regionEvidence:'unknown',regionNote:'源站未提供可验证的出口地区',reason:'没有可用的探测地址'};
 if(!signal.aborted && last.cloudflare)Object.assign(last,await domainRegion(new URL('https://'+last.probeHost),source,route,last.routeMode==='inherit'?last.legacy:{},{signal,fetchImpl}));
 delete last.cloudflare;delete last.legacy;delete last.routeMode;
 return last;
}
module.exports={catalog,targetFor,probeSource,domainRegion};
