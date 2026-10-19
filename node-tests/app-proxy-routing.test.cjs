'use strict';
const test=require('node:test');
const assert=require('node:assert/strict');
const http=require('node:http');
const net=require('node:net');
const zlib=require('node:zlib');
const fs=require('node:fs');
const os=require('node:os');
const path=require('node:path');
const {once}=require('node:events');
const {AsyncLocalStorage}=require('node:async_hooks');
const routing=require('../app/src/main/assets/nodejs-project/app-proxy-routing.js');
const {installBridge}=require('../app/src/main/assets/nodejs-project/app-outbound-bridge.js');
async function read(socket,size){for(;;){const data=socket.read(size);if(data)return data;if(socket.destroyed||socket.readableEnded)throw new Error('closed');await once(socket,'readable');}}
async function listen(server){server.listen(0,'127.0.0.1');await once(server,'listening');return server.address().port;}
async function fixture(type,action,{reject=false,slow=false}={}) {
 const sockets=new Set(),seen=[],tunnels=[];
 const origin=http.createServer((req,res)=>{
  const chunks=[];req.on('data',c=>chunks.push(c));req.on('end',()=>{
   seen.push({host:req.headers.host,path:req.url,method:req.method,headers:req.headers,body:Buffer.concat(chunks).toString()});
   if(req.url==='/redirect'){res.writeHead(302,{location:'http://other.test/final'});res.end();return;}
   if(req.url==='/redirect307'){res.writeHead(307,{location:'http://other.test/final'});res.end();return;}
   if(req.url==='/slow'){res.writeHead(200);res.write('start');return;}
   const data=Buffer.from(JSON.stringify(seen.at(-1)));
   res.setHeader('set-cookie',['one=1','two=2']);
   if(req.url==='/gzip'){res.setHeader('content-encoding','gzip');res.end(zlib.gzipSync(data));}else res.end(data);
  });
 });
 const originPort=await listen(origin);
 function track(s){sockets.add(s);s.on('error',()=>{});s.once('close',()=>sockets.delete(s));return s;}
 origin.on('connection',track);
 const server=net.createServer(socket=>{
  track(socket);
  (async()=>{
   if(slow){socket.resume();return;}
   let forwarded='';
   if(type==='socks5') {
    const greeting=await read(socket,2);await read(socket,greeting[1]);socket.write(Buffer.from([5,2]));
    const auth=await read(socket,2);const user=(await read(socket,auth[1])).toString();const length=(await read(socket,1))[0];const pass=(await read(socket,length)).toString();
    assert.equal(user,'user');assert.equal(pass,'pass');socket.write(Buffer.from([1,reject?1:0]));if(reject){socket.end();return;}
    const request=await read(socket,5);assert.equal(request[3],3);
    const host=(await read(socket,request[4])).toString();const port=await read(socket,2);tunnels.push({host,port:port.readUInt16BE()});
    socket.write(Buffer.from([5,0,0,1,127,0,0,1,0,80]));
   } else {
    let text='';while(!text.endsWith('\r\n\r\n'))text+=(await read(socket,1)).toString();
    assert.match(text,/Proxy-Authorization: Basic dXNlcjpwYXNz/);
    const [method,target]=text.split(' ');assert.notEqual(method,'CONNECT');
    const url=new URL(target);tunnels.push({host:url.hostname});
    forwarded=text.replace(target,url.pathname+url.search).replace(/^Proxy-Authorization:.*\r\n/im,'');
    if(reject){socket.end('HTTP/1.1 407 Proxy Authentication Required\r\n\r\n');return;}

   }
   const upstream=track(net.connect(originPort,'127.0.0.1'));
   socket.on('close',()=>upstream.destroy());upstream.on('close',()=>socket.destroy());
   if(forwarded)upstream.write(forwarded);
   socket.pipe(upstream);upstream.pipe(socket);
  })().catch(error=>socket.destroy(error));
 });
 const port=await listen(server);
 const config={enabled:false,proxy:{enabled:true,nodes:[{id:'fixture',name:'Fixture',type,host:'127.0.0.1',port,username:'user',password:'pass'}],rules:{aiyifan:'node:fixture'},defaultRoute:'inherit'}};
 const bridge=installBridge({snapshot:{config}});
 try {await action({seen,tunnels,bridge,config});}
 finally {bridge.stop();for(const socket of sockets)socket.destroy();origin.closeAllConnections();await Promise.all([new Promise(r=>server.close(r)),new Promise(r=>origin.close(r))]);}
}
for(const type of ['http','socks5']) {
 test(type+' proxy auth, source rule, remote DNS, POST, cookies and decompression',()=>fixture(type,async({seen,tunnels})=>{
  const response=await routing.withSource('aiyifan',()=>fetch('http://source.test/gzip',{method:'POST',body:'exactly once',headers:{'Proxy-Authorization':'must-not-leak'}}));
  assert.equal(response.headers.getSetCookie().length,2);assert.equal(response.url,'http://source.test/gzip');
  const data=await response.json();assert.equal(data.body,'exactly once');assert.equal(data.headers['proxy-authorization'],undefined);
  assert.equal(seen.length,1);assert.equal(tunnels[0].host,'source.test');
  const result=await routing.withSource('aiyifan',()=>new Promise((resolve,reject)=>{
   const req=http.request('http://source.test/raw',{method:'POST'},res=>{let text='';res.on('data',c=>text+=c);res.on('end',()=>resolve(JSON.parse(text)));});req.on('error',reject);req.end('stream body');
  }));assert.equal(result.body,'stream body');
 }));
 test(type+' 307 retains one POST per hop and body cancellation closes the tunnel',()=>fixture(type,async({seen})=>{
  const response=await routing.withSource('aiyifan',()=>fetch('http://source.test/redirect307',{method:'POST',body:'same body'}));
  const cloned=response.clone();assert.equal(cloned.url,response.url);assert.equal((await cloned.json()).body,'same body');await response.text();
  assert.equal(seen.length,2);assert.deepEqual(seen.map(x=>[x.method,x.body]),[['POST','same body'],['POST','same body']]);
  const abort=new AbortController();
  const slow=await routing.withSource('aiyifan',()=>fetch('http://source.test/slow',{signal:abort.signal}));
  const reading=slow.text();abort.abort();await assert.rejects(reading);
 }));
 test(type+' redirect preserves source route and strips cross-origin secrets',()=>fixture(type,async({seen})=>{
  const response=await routing.withSource('aiyifan',()=>fetch('http://source.test/redirect',{headers:{Authorization:'token',Cookie:'cookie'}}));
  assert.equal(response.redirected,true);assert.equal(response.url,'http://other.test/final');await response.text();
  assert.equal(seen.length,2);assert.equal(seen[1].headers.authorization,undefined);assert.equal(seen[1].headers.cookie,undefined);
 }));
 test(type+' service stop closes an active proxy response',()=>fixture(type,async({bridge})=>{
  const response=await routing.withSource('aiyifan',()=>fetch('http://source.test/slow'));
  const reading=response.text();bridge.stop();await assert.rejects(reading);
 }));
 test(type+' rejection never falls back to the source',()=>fixture(type,async({seen})=>{
  await assert.rejects(routing.withSource('aiyifan',()=>fetch('http://source.test/no-direct')),/认证|密码错误/);assert.equal(seen.length,0);
 },{reject:true}));
 test(type+' cancels proxy handshake',()=>fixture(type,async()=>{
  await assert.rejects(routing.withSource('aiyifan',()=>fetch('http://source.test/slow',{signal:AbortSignal.timeout(60)})));
 },{slow:true}));
}
test('routing aliases, localhost exclusion, deleted binding, inherited legacy rule and concurrent source isolation',async()=>{
 const config={enabled:true,nodes:[],rules:{imgo:'direct',aiyifan:'node:deleted'},defaultRoute:'node:default'};
 assert.equal(routing.withSource('mango',()=>routing.routeFor(new URL('https://source.test'),config)).mode,'direct');
 assert.equal(routing.withSource('imgo',()=>routing.routeFor(new URL('https://source.test'),config)).mode,'direct');
 assert.equal(routing.routeFor(new URL('http://127.0.0.1'),config).mode,'inherit');
 assert.equal(routing.routeFor(new URL('https://api.tripdata.app'),config).node,undefined);
 assert.equal(routing.withRoute({mode:'direct'},()=>routing.routeFor(new URL('https://other.test'),config)).mode,'direct');
 const values=await Promise.all(['aiyifan','mango'].map(source=>routing.withSource(source,async()=>{await new Promise(r=>setTimeout(r,5));return routing.routeFor(new URL('https://source.test'),config).mode;})));
 assert.deepEqual(values,['proxy','direct']);
});
test('core proxy sources inherit core config over an App-wide default; direct remains per-source',()=>{
 const node={id:'one',name:'one',type:'http',host:'127.0.0.1',port:8080};
 const config={enabled:true,defaultRoute:'node:one',nodes:[node],rules:{}};
 for(const source of routing.coreProxySources)assert.equal(routing.withSource(source,()=>routing.routeFor(new URL('https://source.test'),config)).mode,'inherit');
 assert.equal(routing.withSource('douban',()=>routing.routeFor(new URL('https://source.test'),config)).mode,'proxy');
 config.rules.tmdb='direct';
 assert.equal(routing.withSource('tmdb',()=>routing.routeFor(new URL('https://api.tmdb.org'),config)).mode,'direct');
});
test('core source context works without editing core; explicit routes bypass legacy URL rewriting',async()=>{
 const directory=fs.mkdtempSync(path.join(os.tmpdir(),'app-proxy-core-'));
 try {
  fs.mkdirSync(path.join(directory,'utils'));fs.mkdirSync(path.join(directory,'configs'));
  fs.writeFileSync(path.join(directory,'package.json'),'{"type":"module"}');
  fs.writeFileSync(path.join(directory,'utils/http-util.js'),'import {AsyncLocalStorage} from "node:async_hooks";export const sourceLogContext=new AsyncLocalStorage();');
  fs.writeFileSync(path.join(directory,'configs/globals.js'),'export const Globals={makeProxyUrl(url){return "https://legacy.test/"+url}};export const globals=Globals;');
  await routing.bindCoreSourceContext(directory);
  let selected='node:one';
  routing.setProxySettingsReader(()=>({enabled:true,rules:{aiyifan:selected}}));
  const {pathToFileURL}=require('node:url');
  const {sourceLogContext}=await import(pathToFileURL(path.join(directory,'utils/http-util.js')));
  const {Globals}=await import(pathToFileURL(path.join(directory,'configs/globals.js')));
  assert.equal(sourceLogContext.run('aiyifan',()=>routing.sourceFor(new URL('https://new-host.test'))),'aiyifan');
  assert.equal(sourceLogContext.run('aiyifan',()=>Globals.makeProxyUrl('https://source.test')),'https://source.test');
  selected='inherit';
  assert.equal(sourceLogContext.run('aiyifan',()=>Globals.makeProxyUrl('https://source.test')),'https://legacy.test/https://source.test');
  selected='direct';
  assert.equal(sourceLogContext.run('aiyifan',()=>Globals.makeProxyUrl('https://source.test')),'https://source.test');
  assert.equal(sourceLogContext.run('douban',()=>Globals.makeProxyUrl('https://source.test')),'https://legacy.test/https://source.test');
 }finally{await routing.bindCoreSourceContext('');fs.rmSync(directory,{recursive:true,force:true});}
});

test('global direct defaults migrate to core inheritance; per-source direct and App node overrides remain explicit',()=>{
 const config=routing.validateProxyConfig({enabled:true,routeVersion:2,defaultRoute:'direct',rules:{aiyifan:'direct',tmdb:'node:one'},
  nodes:[{id:'one',type:'http',host:'proxy.test',port:2080,username:'user',password:'pass'}]});
 assert.equal(config.defaultRoute,'inherit');assert.equal(config.rules.aiyifan,'direct');
 assert.equal(routing.routeFor(new URL('https://api.tripdata.app'),config).mode,'direct');
 const selected=routing.routeFor(new URL('https://api.tmdb.org'),config);
 assert.equal(selected.mode,'proxy');assert.equal(selected.node.id,'one');assert.equal(selected.node.password,'pass');
 const defaults={...config,defaultRoute:'node:one'};
 assert.equal(routing.routeFor(new URL('https://api.tripdata.app'),defaults).mode,'direct');
 assert.equal(routing.routeFor(new URL('https://unlisted.test'),defaults).node.id,'one');
});
test('old per-source direct rules migrate to core inheritance once',()=>{
 const config=routing.validateProxyConfig({enabled:true,rules:{aiyifan:'direct'}});
 assert.equal(config.routeVersion,2);assert.equal(config.rules.aiyifan,'inherit');
});
test('core proxy parsing distinguishes authenticated forward URLs from source and universal reverse rules',()=>{
 const forward='https://u%40ser:p%3Aass@proxy.test:2080/';
 const raw='aiyifan@https://reverse.test/, @https://universal.test/, '+forward;
 assert.equal(routing.coreProxyConfig(raw).forward,forward);
 assert.equal(routing.coreProxyConfig(raw).entries.length,3);
 for(const raw of ['', '@https://universal.test/','aiyifan@https://reverse.test/'])assert.equal(routing.coreProxyConfig(raw).forward,'');
});
