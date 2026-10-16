#!/usr/bin/env node
'use strict';
// Explicit, opt-in network smoke. Run with the embedded Node CLI and
// --experimental-quic; it never installs/restarts an App or changes VPN/proxy.
const assert=require('node:assert/strict');
const {installBridge}=require('../app/src/main/assets/nodejs-project/app-outbound-bridge.js');
const {NativeTransport,startNativeOutbound,nativeCapabilities}=require('../app/src/main/assets/nodejs-project/app-outbound-native.js');
const {diagnoseSource}=require('../app/src/main/assets/nodejs-project/app-outbound-diagnostics.js');

async function main() {
  const mode=process.argv[2]||'auto';
  if(!['auto','h2','h3'].includes(mode))throw new Error('Usage: node --experimental-quic scripts/smoke-native-outbound.cjs [auto|h2|h3] [source...]');
  const sources=process.argv.slice(3);
  const selected=sources.length?sources:['bahamut','tmdb','dandan','animeko','douban'];
  assert(selected.every(source=>['bahamut','tmdb','dandan','animeko','douban'].includes(source)));
  const capabilities=nativeCapabilities();
  assert(capabilities.ech && capabilities.quic,'Requires embedded Node with ECH and native QUIC');
  const connectTimeoutMs=Number(process.env.DANMU_OUTBOUND_CONNECT_TIMEOUT_MS||3000);
  assert(Number.isInteger(connectTimeoutMs) && connectTimeoutMs>=1 && connectTimeoutMs<=60000,'Invalid connection timeout');
  const config={enabled:true,httpVersion:mode,sources:selected,dohUrl:'',connectTimeoutMs};
  const transport=new NativeTransport(config,{capabilities});
  const backend=await startNativeOutbound(config,{transport});
  const snapshot={config,status:'ready',...backend};
  const bridge=installBridge({snapshot,env:()=>({})});
  let failed=false;
  try {
    console.log(JSON.stringify({node:process.version,openssl:process.versions.openssl,mode,connectTimeoutMs,parallel:process.env.DANMU_OUTBOUND_PARALLEL==='1'}));
    const check=async source=>{
      const result=await diagnoseSource(source,snapshot,{},{});
      const sessions=[...transport.states].filter(([,state])=>state.session).map(([host,state])=>({host,protocol:state.session.protocol,ech:state.session.ech}));
      console.log(JSON.stringify({source,...result,sessions}));
      failed ||= !result.ok;
    };
    if(process.env.DANMU_OUTBOUND_PARALLEL==='1')await Promise.all(selected.map(check));
    else for(const source of selected)await check(source);
  } finally {bridge.stop();await backend.close();}
  if(failed)process.exitCode=1;
}
main().catch(error=>{console.error(error.code||error.message);process.exitCode=1;});
