'use strict';
const test=require('node:test');
const assert=require('node:assert/strict');
const fs=require('node:fs');
const os=require('node:os');
const path=require('node:path');
const net=require('node:net');
const {spawn}=require('node:child_process');
const {once}=require('node:events');
const runtimeRoot=path.resolve('app/src/main/assets/nodejs-project');
const delay=ms=>new Promise(resolve=>setTimeout(resolve,ms));
async function waitFor(fn, timeout=15000) {
 const until=Date.now()+timeout;let last;
 while(Date.now()<until){try{const value=await fn();if(value)return value;}catch(error){last=error;}await delay(100);}
 throw last || new Error('Timed out');
}
async function freePort(){const server=net.createServer();server.listen(0,'127.0.0.1');await once(server,'listening');const port=server.address().port;await new Promise(resolve=>server.close(resolve));return port;}
for(const workerEnabled of [true,false]) {
 test(`unmodified core uses App transport; native backend survives core switch (${workerEnabled?'Worker':'direct import'})`,{timeout:25000},async()=>{
  const directory=fs.mkdtempSync(path.join(os.tmpdir(),'app-outbound-host-'));
  let child,logs='';
  try {
   for(const file of fs.readdirSync(runtimeRoot))if(file.endsWith('.js') || file==='package.json' || file==='app-runtime-assets.json')fs.copyFileSync(path.join(runtimeRoot,file),path.join(directory,file));
   fs.symlinkSync(path.join(runtimeRoot,'node_modules'),path.join(directory,'node_modules'));
   fs.mkdirSync(path.join(directory,'config'));
   fs.mkdirSync(path.join(directory,'outbound'));
   const configPath=path.join(directory,'outbound/settings.json');
   fs.writeFileSync(configPath,JSON.stringify({enabled:true,sources:['bahamut','tmdb','dandan','animeko','douban'],httpVersion:'h2'}));
   fs.writeFileSync(path.join(directory,'config/.env'),`TOKEN=\nDANMU_API_VARIANT=stable\nOUTBOUND_MODE=auto\nDANMU_API_WORKER=${workerEnabled}\n`);
   for(const variant of ['stable','custom']){
    const coreDir=path.join(directory,'danmu_api_'+variant);fs.mkdirSync(coreDir);
    fs.writeFileSync(path.join(coreDir,'package.json'),' {"type":"module"}');
    fs.writeFileSync(path.join(coreDir,'worker.js'),`export async function handleRequest(req,env){
      const source=new URL(req.url).searchParams.get('source');
      const host=({dandan:'api.danmaku.weeblify.app',animeko:'danmaku-global.myani.org',douban:'m.douban.com'})[source]||'api.gamer.com.tw';
      const response=await fetch('https://'+host+'/offline-probe');
      return Response.json({variant:'${variant}',coreOutbound:env.OUTBOUND_MODE,result:await response.json()});
    }`);
   }
   // Inject only the upstream session, keeping the real authenticated native
   // adapter and host/Worker wiring. The runtime requires no child executable.
   const nativeFile=path.join(directory,'app-outbound-native.js');
   fs.appendFileSync(nativeFile,`
const realStart=module.exports.startNativeOutbound;
module.exports.startNativeOutbound=config=>realStart(config,{transport:{
 async request(target,method,headers,body,signal,res){
  res.writeHead(200,{'content-type':'application/json'});
  res.end(JSON.stringify({via:'node-native',helperPid:process.pid,targetHost:target.hostname,id:target.hostname==='m.douban.com'?'36296618':400602,title:'offline detail',animes:[{}]}));
 },close(){}
}});
`);
   const port=await freePort();const base=`http://127.0.0.1:${port}`;
   child=spawn(process.execPath,[path.join(directory,'main.js')],{cwd:directory,env:{...process.env,DANMU_API_HOME:directory,DANMU_API_PORT:String(port),DANMU_API_HOST:'127.0.0.1',DANMU_APP_OUTBOUND_CONFIG:configPath,PROXY_URL:''},stdio:['ignore','pipe','pipe']});
   child.stdout.on('data',data=>logs+=data);child.stderr.on('data',data=>logs+=data);
   await waitFor(async()=>{const response=await fetch(base+'/__health');return response.ok;});
   const first=await (await fetch(base+'/api/probe')).json();
   assert.equal(first.variant,'stable');assert.equal(first.coreOutbound,'off');assert.equal(first.result.via,'node-native');
   for(const [source,host] of [['dandan','api.danmaku.weeblify.app'],['animeko','danmaku-global.myani.org'],['douban','m.douban.com']]) {
     const result=await (await fetch(base+'/api/probe?source='+source)).json();
     assert.equal(result.result.targetHost,host);assert.equal(result.result.helperPid,first.result.helperPid);
     const diagnostic=await (await fetch(base+'/__outbound?source='+source,{method:'POST'})).json();
     assert.equal(diagnostic.ok,true);assert.equal(diagnostic.token,undefined);assert.equal(diagnostic.endpoint,undefined);
   }
   const status=await (await fetch(base+'/__outbound')).json();assert.equal(status.status,'ready');assert.equal(status.token,undefined);assert.equal(status.endpoint,undefined);
   fs.writeFileSync(path.join(directory,'config/.env'),`TOKEN=\nDANMU_API_VARIANT=custom\nOUTBOUND_MODE=auto\nDANMU_API_WORKER=${workerEnabled}\n`);
   const next=await waitFor(async()=>{const result=await (await fetch(base+'/api/probe')).json();return result.variant==='custom'?result:null;});
   assert.equal(next.result.helperPid,first.result.helperPid);assert.equal(next.coreOutbound,'off');
   await fetch(base+'/__shutdown');await waitFor(()=>child.exitCode!==null || child.signalCode!==null,7000);
   assert.equal(child.exitCode,0);
   assert.equal(JSON.parse(fs.readFileSync(path.join(directory,'outbound/status.json'))).status,'off');
  } catch(error){error.message+='\nHost output:\n'+logs.slice(-5000);throw error;}
  finally {if(child && child.exitCode===null)child.kill('SIGKILL');fs.rmSync(directory,{recursive:true,force:true});}
 });
}
