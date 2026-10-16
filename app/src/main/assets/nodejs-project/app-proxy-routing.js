'use strict';
// Native HTTP CONNECT / TLS / SOCKS5 transport. No executable helper.
const http = require('node:http');
const https = require('node:https');
const net = require('node:net');
const tls = require('node:tls');
const { once } = require('node:events');
const { Readable } = require('node:stream');
const zlib = require('node:zlib');
const { AsyncLocalStorage } = require('node:async_hooks');
const originalRequest = { 'http:':http.request, 'https:':https.request };
const explicitSource = new AsyncLocalStorage();
let sourceContext;
let boundCore;
let proxySettingsReader = () => undefined;
const forcedRoute = new AsyncLocalStorage();
function setProxySettingsReader(reader) { proxySettingsReader = reader; }
function withRoute(route, action) { return forcedRoute.run(route, action); }
const aliases = { '360kan':'360', mango:'imgo', ani:'animeko' };
const hostSources = new Map([
  ['api.gamer.com.tw','bahamut'],['api.tmdb.org','tmdb'],['api.themoviedb.org','tmdb'],
  ['api.danmaku.weeblify.app','dandan'],['nipaplay.aimes-soft.com','dandan'],
  ['m.douban.com','douban'],['api.douban.com','douban'],
  ['api.animeko.org','animeko'],['danmaku-global.myani.org','animeko'],['danmaku-cn.myani.org','animeko'],
  ['s1.animeko.openani.org','animeko'],['api.bangumi.vip','animeko'],
  ['api.tripdata.app','aiyifan'],['rankv21.tripdata.app','aiyifan'],
]);
const localHost = host => ['localhost','127.0.0.1','::1','[::1]'].includes(host);
const coreProxySources = new Set(['bahamut','tmdb','bilibili','animeko','aiyifan']);
// Shared by the host helper and source probes. An auth '@' is part of a forward URL.
function coreProxyConfig(raw) {
  const entries = String(raw || '').split(',').map(value => value.trim()).filter(Boolean);
  return {entries, forward:entries.find(value => /^https?:\/\//i.test(value)) || ''};
}
const migrateRoute = route => route === 'direct' ? 'inherit' : route;
function validateProxyConfig(input = {}) {
  const rawNodes = Array.isArray(input.nodes) ? input.nodes : [];
  if (rawNodes.length > 1000) throw new Error('代理节点不能超过 1000 个');
  const nodes = rawNodes.flatMap(node => {
    if (!['http','https','socks5'].includes(node.type)) return [];
    const old = node.protocolOptions || {};
    if (Object.keys(old).some(key => !['tls','sni','servername','skip-cert-verify','udp','tfo'].includes(key))) return [];
    return [{id:node.id,name:node.name,type:node.type,host:node.host,port:node.port,
      username:node.username || '',password:node.password || '',
      tls:node.tls === true || (node.type === 'socks5' && old.tls === true),
      tlsServerName:node.tlsServerName ?? old.sni ?? old.servername ?? '',
      skipCertVerify:node.skipCertVerify === true || old['skip-cert-verify'] === true}];
  });
  if (nodes.length > 1000) throw new Error('代理节点不能超过 1000 个');
  const ids = new Set();
  for (const node of nodes) {
    if (!node.id || ids.has(node.id)) throw new Error('代理节点标识无效或重复');
    ids.add(node.id);
    if (typeof node.tlsServerName !== 'string' || /[\s/@?#:]/.test(node.tlsServerName)) throw new Error('TLS 服务器名称无效');
    if (typeof node.host !== 'string' || !node.host.trim() || /[\s/@\r\n]/.test(node.host)) throw new Error('代理地址无效');
    if (!Number.isInteger(node.port) || node.port < 1 || node.port > 65535) throw new Error('代理端口无效');
    for (const field of ['username','password']) if (Buffer.byteLength(node[field] || '') > 255 || /[\r\n]/.test(node[field] || '')) throw new Error('代理凭据无效');
  }
  const validRule = value => typeof value === 'string' && value.length <= 200 && !/[\r\n]/.test(value);
  const rules = input.rules && typeof input.rules === 'object' && !Array.isArray(input.rules) ? input.rules : {};
  if (Object.keys(rules).length > 1000 || !Object.values(rules).every(validRule)) throw new Error('来源分流规则无效');
  const defaultRoute = input.defaultRoute || 'inherit';
  if (!validRule(defaultRoute)) throw new Error('默认线路无效');
  return { enabled:input.enabled === true, defaultRoute:migrateRoute(defaultRoute), rules:Object.fromEntries(Object.entries(rules).map(([source,rule])=>[source,migrateRoute(rule)])), nodes };
}
async function bindCoreSourceContext(directory) {
  const { pathToFileURL } = require('node:url');
  const path = require('node:path');
  sourceContext = undefined;
  boundCore = undefined;
  try { sourceContext = (await import(pathToFileURL(path.join(directory,'utils/http-util.js')).href)).sourceLogContext; } catch {}
  for (const relative of ['configs/globals.js','config/globals.js']) {
    try {
      const mod=await import(pathToFileURL(path.join(directory,relative)).href);
      const core=mod.Globals || mod.globals;
      if (!core || typeof core.makeProxyUrl !== 'function') continue;
      if (!core.makeProxyUrl.__appRouting) {
        const original=core.makeProxyUrl;
        const wrapped=function(target,...args) {
          try {if(routeFor(new URL(target),proxySettingsReader()).mode !== 'inherit')return target;}catch{}
          return original.call(this,target,...args);
        };
        wrapped.__appRouting=true;core.makeProxyUrl=wrapped;
      }
      boundCore={directory:path.resolve(directory),core,aiyifanTarget:''};
      break;
    } catch {}
  }
  if (boundCore) {
    try {
      const source=await import(pathToFileURL(path.join(directory,'sources/aiyifan.js')).href);
      let constants={};
      try {constants=await import(pathToFileURL(path.join(directory,'utils/aiyifan-util.js')).href);}catch{}
      const constructor=source.default || source.AiyifanSource;
      if(typeof constructor==='function')boundCore.aiyifanTarget=declaredAiyifanTarget(Function.prototype.toString.call(constructor),constants);
    }catch{}
  }
}
function declaredAiyifanTarget(content, constants) {
  const assignment=content.match(/\bthis\.DETAILS_API\s*=\s*(?:([A-Z][A-Z0-9_]*)\s*\+\s*)?['"]([^'"\r\n]+)['"]/);
  if(!assignment)return '';
  const base=assignment[1]?constants[assignment[1]]:'';
  if(typeof base!=='string')return '';
  try {return new URL(base+assignment[2]).href;}catch{return '';}
}
function coreProbeTarget(source,directory) {
  if(source==='aiyifan' && boundCore?.directory===require('node:path').resolve(directory))return boundCore.aiyifanTarget;
}
// Resolve in the loaded core's own context, including its initialized config.
// Do not emulate PROXY_URL: old, new and custom cores may interpret it differently.
function resolveCoreTarget(target, source, directory, env) {
  if (!boundCore || boundCore.directory !== require('node:path').resolve(directory)) return null;
  if (env && typeof boundCore.core.init==='function') boundCore.core.init(env);
  const value=withSource(source,()=>withRoute({mode:'inherit'},()=>boundCore.core.makeProxyUrl(target)));
  if (typeof value !== 'string') throw new Error('核心未返回可识别的线路');
  const url=new URL(value);
  if (!['http:','https:'].includes(url.protocol) || url.username || url.password) throw new Error('核心线路地址无效');
  const forward=localHost(url.hostname) && url.pathname==='/proxy' && url.searchParams.get('url')===target;
  return {url:url.href,kind:url.href===new URL(target).href?'':forward?'核心 HTTP 代理':'核心反向代理',forward};
}
function sourceFor(target) {
  const source = explicitSource.getStore() || sourceContext?.getStore();
  if (source && source !== 'system') return aliases[source] || String(source);
  return hostSources.get(target.hostname) || '';
}
function routeFor(target, proxy, source = sourceFor(target)) {
  if (localHost(target.hostname)) return { mode:'inherit', source };
  if (forcedRoute.getStore()) return {...forcedRoute.getStore(),source};
  if (!proxy?.enabled) return { mode:'inherit', source };
  // An explicit source choice also overrides old App default-node rules.
  const rule = migrateRoute(proxy.rules?.[source] ?? proxy.defaultRoute ?? 'inherit');
  if (['inherit','enhanced'].includes(rule)) return { mode:rule, source };
  const node = proxy.nodes?.find(item => rule === 'node:' + item.id);
  return { mode:'proxy', node, source }; // A deleted binding fails closed.
}
function withSource(source, action) { return explicitSource.run(source, action); }
async function readExact(socket, size, signal) {
  while (true) {
    signal.throwIfAborted();
    const part = socket.read(size);
    if (part) return part;
    if (socket.destroyed || socket.readableEnded) throw new Error('代理握手提前结束');
    await once(socket,'readable',{signal});
  }
}
async function tunnel(node, host, port, signal) {
  const socket=await connectProxyNode(node,signal);
  const abort=()=>socket.destroy(signal.reason);
  signal.addEventListener('abort',abort,{once:true});
  try {
    signal.throwIfAborted();
    await proxyHandshake(socket,node,host,port,signal);
    return socket;
  } catch(error) {socket.destroy();throw error;}
  finally {signal.removeEventListener('abort',abort);}
}
async function connectProxyNode(node, signal) {
  if (!node) throw new Error('绑定的代理节点已失效，请重新选择');
  if (!['http','https','socks5'].includes(node.type)) throw new Error('节点协议不受支持，请选择 HTTP、HTTPS 或 SOCKS5');
  const address = node.host.replace(/^\[|\]$/g,'');
  const secureProxy = node.type === 'https' || (node.type === 'socks5' && node.tls);
  const tlsHost = node.tlsServerName || address;
  const socket = secureProxy
    ? tls.connect({host:address, port:node.port, servername:net.isIP(tlsHost) ? undefined : tlsHost, rejectUnauthorized:node.skipCertVerify !== true})
    : net.connect({host:address, port:node.port});
  const abort = () => socket.destroy(signal.reason);
  socket.on('error',()=>{});
  signal.addEventListener('abort',abort,{once:true});
  try {
    signal.throwIfAborted();
    await once(socket,secureProxy ? 'secureConnect' : 'connect',{signal});
    return socket;
  } catch (error) { socket.destroy(); throw error; }
  finally { signal.removeEventListener('abort',abort); }
}
async function proxyHandshake(socket,node,host,port,signal) {
    if (node.type === 'socks5') {
      socket.write(Buffer.from(node.username ? [5,2,0,2] : [5,1,0]));
      const greeting = await readExact(socket,2,signal);
      if (greeting[0] !== 5 || ![0,2].includes(greeting[1])) throw new Error('SOCKS5 认证方式不受支持');
      if (greeting[1] === 2) {
        const user=Buffer.from(node.username || ''), pass=Buffer.from(node.password || '');
        socket.write(Buffer.concat([Buffer.from([1,user.length]),user,Buffer.from([pass.length]),pass]));
        const auth=await readExact(socket,2,signal);
        if (auth[0] !== 1 || auth[1] !== 0) throw new Error('SOCKS5 用户名或密码错误');
      }
      const name=Buffer.from(host.replace(/^\[|\]$/g,''));
      if (name.length > 255) throw new Error('目标域名过长');
      socket.write(Buffer.concat([Buffer.from([5,1,0,3,name.length]),name,Buffer.from([port>>8,port&255])]));
      const reply=await readExact(socket,4,signal);
      if (reply[0] !== 5 || reply[1] !== 0) throw new Error('SOCKS5 无法连接目标 ('+reply[1]+')');
      if (reply[3] === 1) await readExact(socket,6,signal);
      else if (reply[3] === 4) await readExact(socket,18,signal);
      else if (reply[3] === 3) { const len=(await readExact(socket,1,signal))[0];await readExact(socket,len+2,signal); }
      else throw new Error('SOCKS5 响应地址无效');
    } else {
      const authority=(host.includes(':') && !host.startsWith('[') ? '['+host+']' : host)+':'+port;
      const auth=node.username ? 'Proxy-Authorization: Basic '+Buffer.from(node.username+':'+(node.password || '')).toString('base64')+'\r\n' : '';
      socket.write('CONNECT '+authority+' HTTP/1.1\r\nHost: '+authority+'\r\n'+auth+'\r\n');
      let headers='';
      while (!headers.endsWith('\r\n\r\n')) {
        if (headers.length >= 16384) throw new Error('代理响应头过大');
        headers+=(await readExact(socket,1,signal)).toString('latin1');
      }
      const status=Number(headers.match(/^HTTP\/1\.[01] (\d{3})/)?.[1]);
      if (status !== 200) throw new Error(status === 407 ? 'HTTP 代理认证失败' : 'HTTP 代理隧道失败 ('+(status || '无效响应')+')');
    }
}
function requestThroughProxy(target, options, node, callback) {
  if (!node) throw new Error('绑定的代理节点已失效，请重新选择');
  const secure = target.protocol === 'https:';
  const forward=!secure && ['http','https'].includes(node.type);
  const wireProtocol=forward ? node.type+':' : target.protocol;
  const agent = wireProtocol==='https:' ? new https.Agent({keepAlive:false}) : new http.Agent({keepAlive:false});
  agent.createConnection = (params, done) => {
    const signal = AbortSignal.any([...(options.signal ? [options.signal] : []),AbortSignal.timeout(12000)]);
    const connection=forward ? connectProxyNode(node,signal) : tunnel(node,target.hostname,Number(target.port || (secure ? 443 : 80)),signal);
    connection.then(socket => {
      if (!secure) {done(null,socket);return;}
      if(signal.aborted){socket.destroy();done(signal.reason);return;}
      const inner=tls.connect({socket,servername:net.isIP(target.hostname) ? undefined : target.hostname,
        rejectUnauthorized:true, ALPNProtocols:['http/1.1']});
      const abort=()=>inner.destroy(signal.reason);
      signal.addEventListener('abort',abort,{once:true});
      let settled=false;
      const finish=(error)=>{if(settled)return;settled=true;signal.removeEventListener('abort',abort);done(error,error?undefined:inner);};
      inner.once('secureConnect',()=>finish(null));
      inner.once('error',error=>finish(error));
    }, done);
  };
  const headers = {};
  if (Array.isArray(options.headers)) {
    for (let i=0;i<options.headers.length;i+=2)headers[options.headers[i]]=options.headers[i+1];
  } else if (options.headers instanceof Headers) Object.assign(headers,Object.fromEntries(options.headers));
  else Object.assign(headers,options.headers);
  for (const key of Object.keys(headers)) if (/^(proxy-authorization|proxy-connection)$/i.test(key) || /^x-danmu-outbound-/i.test(key)) delete headers[key];
  let request;
  if (forward) {
    for (const key of Object.keys(headers)) if (/^host$/i.test(key)) delete headers[key];
    headers.Host=target.host;
    if(node.username)headers['Proxy-Authorization']='Basic '+Buffer.from(node.username+':'+(node.password || '')).toString('base64');
    request=originalRequest[wireProtocol]({...options,protocol:wireProtocol,hostname:node.host.replace(/^\[|\]$/g,''),host:node.host,port:node.port,
      path:target.origin+target.pathname+target.search,headers,agent},callback);
  } else request=originalRequest[target.protocol](target,{...options,headers,agent},callback);
  request.once('close',()=>agent.destroy());
  return request;
}
async function proxyFetch(input, init, route, nativeFetch) {
  if (!route.node) throw new Error('绑定的代理节点已失效，请重新选择');
  let request=new Request(input,init);
  const overall=AbortSignal.any([request.signal,...(route.stopSignal ? [route.stopSignal] : []),AbortSignal.timeout(60000)]);
  let body;
  if (request.body) {
    const reader=request.body.getReader(),chunks=[];let size=0;
    const abort=()=>reader.cancel(overall.reason).catch(()=>{});
    overall.addEventListener('abort',abort,{once:true});
    try {for(;;){overall.throwIfAborted();const part=await reader.read();overall.throwIfAborted();if(part.done)break;size+=part.value.byteLength;if(size>32*1024*1024)throw new RangeError('代理请求体超过 32 MiB');chunks.push(Buffer.from(part.value));}body=Buffer.concat(chunks,size);}
    catch(error){await reader.cancel(error).catch(()=>{});throw error;}
    finally{overall.removeEventListener('abort',abort);reader.releaseLock();}
  }
  let replay=body, redirected=false;
  for (let count=0;count<=20;count++) {
    const target=new URL(request.url);
    if (!['http:','https:'].includes(target.protocol) || target.username || target.password) throw new Error('代理目标地址无效');
    // Redirects retain the source policy; loopback must never enter a remote tunnel.
    if (localHost(target.hostname)) throw new Error('代理请求不能重定向到本机地址');
    overall.throwIfAborted();
    const response=await new Promise((resolve,reject)=>{
      const headers=Object.fromEntries(request.headers);
      for(const key of Object.keys(headers))if(/^(host|connection|transfer-encoding)$/i.test(key))delete headers[key];
      const outgoing=requestThroughProxy(target,{method:request.method,headers,signal:overall},route.node,incoming=>{
        if(incoming.statusCode===407) {incoming.destroy();reject(new Error('HTTP 代理认证失败'));return;}
        const resultHeaders=new Headers();
        for(let i=0;i<incoming.rawHeaders.length;i+=2)resultHeaders.append(incoming.rawHeaders[i],incoming.rawHeaders[i+1]);
        let stream=incoming;
        const encoding=resultHeaders.get('content-encoding');
        if (request.method !== 'HEAD' && ![204,205,304].includes(incoming.statusCode)) {
          const decoder=encoding === 'gzip' ? zlib.createGunzip() : encoding === 'deflate' ? zlib.createInflate() : encoding === 'br' ? zlib.createBrotliDecompress() : null;
          if(decoder){incoming.on('error',error=>decoder.destroy(error));decoder.on('close',()=>incoming.destroy());stream=incoming.pipe(decoder);}
        }
        const noBody=request.method === 'HEAD' || [204,205,304].includes(incoming.statusCode);
        if(noBody)incoming.resume();
        const response=new Response(noBody ? null : Readable.toWeb(stream),{status:incoming.statusCode,headers:resultHeaders});
        Object.defineProperties(response,{url:{value:target.href,configurable:true},redirected:{value:redirected,configurable:true}});
        resolve(response);
      });
      outgoing.on('error',reject);
      outgoing.end(replay);
    });
    const location=response.headers.get('location');
    if (!location || ![301,302,303,307,308].includes(response.status) || request.redirect === 'manual') return response;
    if (request.redirect === 'error' || count === 20) {await response.body?.cancel();throw new Error('代理请求重定向失败');}
    const next=new URL(location,target);
    const headers=new Headers(request.headers);
    if(next.origin !== target.origin)for(const name of ['authorization','cookie','proxy-authorization'])headers.delete(name);
    let method=request.method;
    if((response.status === 303 && method !== 'HEAD') || ([301,302].includes(response.status) && method === 'POST')) {
      method='GET';replay=undefined;headers.delete('content-type');headers.delete('content-length');
    }
    await response.body?.cancel();
    request=new Request(next,{method,headers,body:replay,signal:overall,redirect:request.redirect});
    redirected=true;
  }
}
module.exports={coreProxySources,coreProxyConfig,validateProxyConfig,routeFor,sourceFor,withSource,withRoute,setProxySettingsReader,bindCoreSourceContext,resolveCoreTarget,coreProbeTarget,declaredAiyifanTarget,requestThroughProxy,proxyFetch};
