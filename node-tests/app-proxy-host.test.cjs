'use strict';
const test=require('node:test'),assert=require('node:assert/strict');
const fs=require('node:fs'),os=require('node:os'),path=require('node:path');
const net=require('node:net'),http=require('node:http');
const {spawn}=require('node:child_process');const {once}=require('node:events');
const runtimeRoot=path.resolve('app/src/main/assets/nodejs-project');
async function listen(server){server.listen(0,'127.0.0.1');await once(server,'listening');return server.address().port;}
async function until(fn){const end=Date.now()+12000;let error;while(Date.now()<end){try{const result=await fn();if(result)return result;}catch(e){error=e;}await new Promise(r=>setTimeout(r,70));}throw error || new Error('timeout');}
for(const worker of [false,true])test(`proxy routing, core context, live config and source probe (${worker?'Worker':'direct'})`,{timeout:40000},async()=>{
 const directory=fs.mkdtempSync(path.join(os.tmpdir(),'proxy-host-'));let child,logs='';const sockets=new Set(),proxyAuths=[];let count=0;
 const upstream=http.createServer((req,res)=>{let body='';req.on('data',chunk=>body+=chunk);req.on('end',()=>{count++;res.end(JSON.stringify({ok:true,path:req.url,method:req.method,body}));});});
 function track(s){sockets.add(s);s.on('error',()=>{});s.on('close',()=>sockets.delete(s));}
 upstream.on('connection',track);const upstreamPort=await listen(upstream);
 const proxy=net.createServer(socket=>{
  track(socket);let header='';const read=chunk=>{header+=chunk.toString();if(!header.includes('\r\n\r\n'))return;socket.removeListener('data',read);
   const [method,target]=header.split(' ');const forward=method!=='CONNECT';
   assert.equal(forward?new URL(target).hostname:target,'source.invalid'+(forward?'':':80'));
   const auth=header.match(/Proxy-Authorization: Basic (\S+)/i)?.[1];proxyAuths.push(auth);
   assert([Buffer.from('user:pass').toString('base64'),Buffer.from('u@ser:p:ass').toString('base64')].includes(auth));
   const remote=net.connect(upstreamPort,'127.0.0.1');track(remote);remote.once('connect',()=>{
    if(forward){const url=new URL(target);remote.write(header.replace(target,url.pathname+url.search).replace(/^Proxy-Authorization:.*\r\n/im,''));}
    else socket.write('HTTP/1.1 200 OK\r\n\r\n');
   });
   socket.pipe(remote);remote.pipe(socket);socket.on('close',()=>remote.destroy());remote.on('close',()=>socket.destroy());};socket.on('data',read);
 });const proxyPort=await listen(proxy);
 try {
  for(const name of fs.readdirSync(runtimeRoot))if(name.endsWith('.js') || name==='package.json' || name==='app-runtime-assets.json')fs.copyFileSync(path.join(runtimeRoot,name),path.join(directory,name));
  fs.symlinkSync(path.join(runtimeRoot,'node_modules'),path.join(directory,'node_modules'));
  fs.mkdirSync(path.join(directory,'config'));const core=path.join(directory,'danmu_api_stable');fs.mkdirSync(core);fs.mkdirSync(path.join(core,'utils'));fs.mkdirSync(path.join(core,'configs'));
  fs.writeFileSync(path.join(core,'package.json'),'{"type":"module"}');
  fs.writeFileSync(path.join(core,'configs/envs.js'),"export class Envs {static ALLOWED_SOURCES=['aiyifan','local','tmdb','custom'];}");
  fs.writeFileSync(path.join(core,'configs/globals.js'),`export const Globals={makeProxyUrl(url){
   if(process.env.PROBE_RESOLVER==='old')return url;
   if(process.env.PROBE_RESOLVER==='slow')return 'https://legacy.invalid/slow';
   if(process.env.PROBE_RESOLVER==='future')return 'https://legacy.invalid/future?target='+encodeURIComponent(url);
   if(process.env.PROXY_URL.split(',').some(x=>/^https?:/.test(x.trim())))return 'http://127.0.0.1:'+process.env.DANMU_API_PROXY_PORT+'/proxy?url='+encodeURIComponent(url);
   return 'https://legacy.invalid/'+url;}};export const globals=Globals;`);
  fs.writeFileSync(path.join(core,'utils/http-util.js'),'import {AsyncLocalStorage} from "node:async_hooks";export const sourceLogContext=new AsyncLocalStorage();');
  fs.writeFileSync(path.join(core,'worker.js'),`import {sourceLogContext} from './utils/http-util.js';import {Globals} from './configs/globals.js';
   export async function handleRequest(req){return sourceLogContext.run('aiyifan',async()=>{try{return Response.json(await (await fetch(Globals.makeProxyUrl('http://source.invalid/live'))).json());}catch{return Response.json({failed:true});}});}`);
  const custom=path.join(directory,'danmu_api_custom');fs.mkdirSync(custom);fs.mkdirSync(path.join(custom,'configs'));fs.mkdirSync(path.join(custom,'utils'));
  for(const name of ['package.json','worker.js'])fs.copyFileSync(path.join(core,name),path.join(custom,name));
  fs.copyFileSync(path.join(core,'configs/envs.js'),path.join(custom,'configs/envs.js'));
  fs.copyFileSync(path.join(core,'utils/http-util.js'),path.join(custom,'utils/http-util.js'));
  fs.writeFileSync(path.join(custom,'configs/globals.js'),`export const Globals={makeProxyUrl(url){return 'https://legacy.invalid/custom/'+url;}};`);
  fs.writeFileSync(path.join(directory,'config/.env'),`TOKEN=\nSOURCE_ORDER=aiyifan,local\nDANMU_API_VARIANT=stable\nDANMU_API_WORKER=${worker}\nPROXY_URL=@https://legacy.invalid\n`);
  const configPath=path.join(directory,'routing.json');
  const config={enabled:false,proxy:{enabled:true,defaultRoute:'inherit',nodes:[{id:'one',name:'node one',type:'http',host:'127.0.0.1',port:proxyPort,username:'user',password:'pass'}],rules:{aiyifan:'node:one',tmdb:'inherit'}}};

  fs.writeFileSync(configPath,JSON.stringify(config));
  const reserved=net.createServer();const port=await listen(reserved);await new Promise(r=>reserved.close(r));const base='http://127.0.0.1:'+port;
  const helperReserved=net.createServer();const helperPort=await listen(helperReserved);await new Promise(r=>helperReserved.close(r));const helperBase='http://127.0.0.1:'+helperPort;
  fs.writeFileSync(path.join(directory,'probe-fixture.cjs'),`const original=globalThis.fetch;globalThis.fetch=async(input,init)=>{
   const url=new URL(typeof input==='string'?input:input.url);
   if(url.hostname==='legacy.invalid' && url.pathname==='/slow'){require('node:fs').writeFileSync(require('node:path').join(process.env.DANMU_API_HOME,'probe-started'),'1');return new Promise((resolve,reject)=>{const abort=()=>{require('node:fs').writeFileSync(require('node:path').join(process.env.DANMU_API_HOME,'probe-cancelled'),'1');reject(init.signal.reason);};if(init.signal.aborted)abort();else init.signal.addEventListener('abort',abort,{once:true});});}
   if(url.hostname==='legacy.invalid' && url.pathname==='/future')return Response.json({ok:true},{status:202});
   if(url.hostname==='legacy.invalid' && url.pathname.startsWith('/custom/'))return Response.json({ok:true},{status:203});
   if(url.hostname==='api.tmdb.org' || (url.hostname==='legacy.invalid' && url.pathname.startsWith('/https://api.tmdb.org/')))return Response.json({status_code:7},{status:url.searchParams.get('api_key')==='fixture-config-key'?200:401});
   return original(input,init);
  };`);
  child=spawn(process.execPath,['--require','./probe-fixture.cjs','android-server.js'],{cwd:directory,env:{...process.env,DANMU_API_HOME:directory,DANMU_API_HOST:'127.0.0.1',DANMU_API_PORT:String(port),DANMU_API_PROXY_PORT:String(helperPort),DANMU_APP_OUTBOUND_CONFIG:configPath},stdio:['ignore','pipe','pipe']});
  child.stdout.on('data',x=>logs+=x);child.stderr.on('data',x=>logs+=x);
  await until(async()=>{const r=await fetch(base+'/__health');return r.ok;});
  const health=await (await fetch(base+'/__health')).json();assert.match(health.networkRuntimeFingerprint,/^[a-f0-9]{64}$/);
  assert.equal(health.networkRuntimeFingerprint,require('../app/src/main/assets/nodejs-project/app-outbound-runtime.js').networkRuntimeFingerprint);
  const result=await (await fetch(base+'/api/test')).json();assert.equal(result.ok,true);assert.equal(count,1);
  const status=await (await fetch(base+'/__outbound')).json();assert.equal(status.config.proxy,undefined);assert(!JSON.stringify(status).includes('pass'));
  const catalog=await (await fetch(base+'/__source-probe')).json();assert.deepEqual(catalog.enabled,['aiyifan','local']);
  const local=await (await fetch(base+'/__source-probe?source=local',{method:'POST'})).json();assert.equal(local.route,'本地文件');assert.equal(local.connected,true);
  const unconfigured=await (await fetch(base+'/__source-probe?source=custom',{method:'POST'})).json();
  assert.equal(unconfigured.status,'unconfigured');assert.equal(unconfigured.configurationRequired,true);
  const tmdbProbe=async()=>await (await fetch(base+'/__source-probe?source=tmdb',{method:'POST',signal:AbortSignal.timeout(1500)})).json();
  const missing=await tmdbProbe();assert.equal(missing.status,'ok');assert.equal(missing.configurationRequired,true);assert.equal(missing.httpStatus,401);
  const envPath=path.join(directory,'config/.env'),originalEnv=fs.readFileSync(envPath,'utf8');
  fs.writeFileSync(envPath,originalEnv+'TMDB_API_KEY=fixture-config-key\n');
  const configured=await until(async()=>{const r=await tmdbProbe();return r.httpStatus===200 && r;});
  assert.equal(configured.status,'ok');assert.notEqual(configured.configurationRequired,true);
  assert(!JSON.stringify(configured).includes('fixture-config-key'));
  fs.writeFileSync(envPath,originalEnv);
  await until(async()=>{const r=await tmdbProbe();return r.httpStatus===401 && r.configurationRequired===true;});
  fs.writeFileSync(envPath,originalEnv+'PROBE_RESOLVER=slow\n');
  const abort=new AbortController();
  let slow;
  await until(async()=>{
   slow=fetch(base+'/__source-probe?source=tmdb',{method:'POST',signal:abort.signal});
   return Promise.race([slow.then(()=>false),new Promise(resolve=>setTimeout(()=>resolve(fs.existsSync(path.join(directory,'probe-started'))),120))]);
  });
  abort.abort();await assert.rejects(slow);
  await until(()=>fs.existsSync(path.join(directory,'probe-cancelled')));
  // Resolve with the actual loaded core, including its Worker-local environment.
  fs.writeFileSync(envPath,originalEnv+'PROBE_RESOLVER=future\n');
  const future=await until(async()=>{const r=await tmdbProbe();if(r.httpStatus!==202)throw new Error('future probe: '+JSON.stringify(r));return r;});
  assert.equal(future.routeType,'core');
  fs.writeFileSync(envPath,originalEnv+'PROBE_RESOLVER=old\n');
  const older=await until(async()=>{const r=await tmdbProbe();return r.routeType==='direct' && r;});
  assert.match(older.reason,/当前核心未对该地址应用/);
  fs.writeFileSync(envPath,originalEnv.replace('DANMU_API_VARIANT=stable','DANMU_API_VARIANT=custom'));
  const switched=await until(async()=>{const r=await tmdbProbe();return r.httpStatus===203 && r;});
  assert.equal(switched.routeType,'core');
  fs.writeFileSync(envPath,originalEnv);
  await until(async()=>{const r=await tmdbProbe();return r.httpStatus===401 && r.routeType==='core';});
  config.proxy.rules.aiyifan='node:deleted';fs.writeFileSync(configPath,JSON.stringify(config));
  await until(async()=>{const r=await (await fetch(base+'/api/test')).json();return r.failed;});
  const stoppedCount=count;
  const failed=await (await fetch(base+'/api/test')).json();assert.equal(failed.failed,true);assert.equal(count,stoppedCount);
  config.proxy.rules.aiyifan='node:one';fs.writeFileSync(configPath,JSON.stringify(config));
  await until(async()=>{const r=await (await fetch(base+'/api/test')).json();return r.ok;});
  // A comma-separated authenticated core proxy can be enabled without restarting the App host.
  const forwardEnv=originalEnv.replace(/PROXY_URL=.*/,`PROXY_URL=tmdb@https://unused.invalid,http://u%40ser:p%3Aass@127.0.0.1:${proxyPort}/`);
  fs.writeFileSync(envPath,forwardEnv);
  const forwarded=await until(async()=>{const response=await fetch(helperBase+'/proxy?url='+encodeURIComponent('http://source.invalid/post'),{method:'POST',body:'signed body'});if(!response.ok)return;return response.json();});
  assert.equal(forwarded.method,'POST');assert.equal(forwarded.body,'signed body');
  const coreAuth=Buffer.from('u@ser:p:ass').toString('base64');
  assert.equal(proxyAuths.at(-1),coreAuth);
  // An explicit App node still overrides a valid, different core forward proxy.
  assert.equal((await (await fetch(base+'/api/test')).json()).ok,true);
  assert.equal(proxyAuths.at(-1),Buffer.from('user:pass').toString('base64'));
  config.proxy.rules.aiyifan='node:deleted';fs.writeFileSync(configPath,JSON.stringify(config));
  await until(async()=>{const r=await (await fetch(base+'/api/test')).json();return r.failed;});
  const stoppedAuths=proxyAuths.length;
  assert.equal((await (await fetch(base+'/api/test')).json()).failed,true);
  assert.equal(proxyAuths.length,stoppedAuths); // A working core proxy is not a fallback for a broken App node.
  config.proxy.rules.aiyifan='inherit';fs.writeFileSync(configPath,JSON.stringify(config));
  await until(async()=>{const r=await (await fetch(base+'/api/test')).json();return r.ok && proxyAuths.at(-1)===coreAuth;});
  // Removing core proxy configuration closes the helper and keeps the main service running.
  fs.writeFileSync(envPath,originalEnv.replace(/PROXY_URL=.*/, 'PROXY_URL='));
  await until(async()=>{try{await fetch(helperBase+'/proxy?url=http://source.invalid/post');return false;}catch{return true;}});
  assert.equal((await fetch(base+'/__health')).ok,true);
  await fetch(base+'/__shutdown');await until(()=>child.exitCode!==null);assert.equal(child.exitCode,0);
 }catch(error){error.message+='\n'+logs.slice(-3500);throw error;}
 finally{if(child?.exitCode===null)child.kill('SIGKILL');for(const socket of sockets)socket.destroy();upstream.closeAllConnections();await Promise.all([new Promise(r=>proxy.close(r)),new Promise(r=>upstream.close(r))]);fs.rmSync(directory,{recursive:true,force:true});}
});
