'use strict';
const fs = require('node:fs');
const path = require('node:path');
const { startNativeOutbound } = require('./app-outbound-native.js');
const { validateProxyConfig } = require('./app-proxy-routing.js');
const { installBridge } = require('./app-outbound-bridge.js');
// Capture once at process start: replacing files cannot make old in-memory code
// claim that it already runs the new APK's network implementation.
const NETWORK_ASSETS = ['app-runtime-assets.json',...require('./app-runtime-assets.json')];
const networkRuntimeFingerprint = (()=>{
  const hash=require('node:crypto').createHash('sha256');
  for(const name of NETWORK_ASSETS){hash.update(name+'\0');hash.update(fs.readFileSync(path.join(__dirname,name)));}
  return hash.digest('hex');
})();

function validateConfig(input = {}) {
  const config = {
    enabled: input.enabled === true,
    proxy: validateProxyConfig(input.proxy),
    sources: Array.isArray(input.sources) ? [...new Set(input.sources)] : ['bahamut','tmdb'],
    httpVersion: String(input.httpVersion || 'auto'),
    dohUrl: String(input.dohUrl || '').trim(),
    connectTimeoutMs: Number(input.connectTimeoutMs ?? 3000),
  };
  if (config.sources.some(source => !['bahamut','tmdb','dandan','animeko','douban'].includes(source))) throw new Error('不支持的增强直连来源');
  if (!['auto','h2','h3'].includes(config.httpVersion)) throw new Error('协议必须为 auto、h2 或 h3');
  if (!Number.isInteger(config.connectTimeoutMs) || config.connectTimeoutMs < 1 || config.connectTimeoutMs > 60000) throw new Error('连接超时必须在 1–60000 毫秒之间');
  if (config.dohUrl) {
    const url = new URL(config.dohUrl);
    if (url.protocol !== 'https:' || url.username || url.password || url.hash) throw new Error('DoH 必须为不含凭据的 HTTPS 地址');
  }
  return config;
}
function createAppOutboundRuntime({ configPath = process.env.DANMU_APP_OUTBOUND_CONFIG, startBackend = startNativeOutbound, onSnapshot = () => {}, log = console.log } = {}) {
  let state = {status:'off', config:validateConfig(), reason:''};
  let backend = null, watcher = null, debounce = null, heartbeat = null, stopped = false, queued = Promise.resolve(), fingerprint = '';
  const bridge = installBridge();
  const statusPath = configPath ? path.join(path.dirname(configPath),'status.json') : '';
  // Heartbeats only touch the local status file; they must not broadcast a
  // complete env snapshot to every Worker when routing has not changed.
  function writeStatus() {
    if (statusPath) {
      const output = {networkRuntimeFingerprint, status:state.status, reason:state.reason || '', pid:process.pid, heartbeat:Date.now(), httpVersion:state.config.httpVersion, sources:state.config.sources};
      try { fs.mkdirSync(path.dirname(statusPath),{recursive:true}); fs.writeFileSync(statusPath+'.'+process.pid+'.pending',JSON.stringify(output),{mode:0o644}); fs.renameSync(statusPath+'.'+process.pid+'.pending',statusPath); } catch {}
    }
  }
  function publish(next = {}) {
    state = {...state,...next};
    bridge.update(state); onSnapshot(state);
    writeStatus();
    if (statusPath && state.config.enabled && !stopped) {
      if (!heartbeat) {
        heartbeat = setInterval(writeStatus, 30000);
        heartbeat.unref?.();
      }
    } else {
      clearInterval(heartbeat);
      heartbeat = null;
    }
  }
  async function stopBackend() {
    const previous=backend;backend=null;
    await previous?.close();
  }
  async function startBackendInstance() {
    backend=await startBackend(state.config);
    publish({status:'ready',reason:'',endpoint:backend.endpoint,token:backend.token,backend:'node'});
  }
  async function apply() {
    if (stopped) return;
    let config;
    try {
      config=validateConfig(configPath && fs.existsSync(configPath) ? JSON.parse(fs.readFileSync(configPath,'utf8')) : {});
    } catch (error) {
      await stopBackend();fingerprint='';
      publish({status:'failed',reason:`增强直连配置无效：${error.message}`,endpoint:'',token:''});return;
    }
    const key=JSON.stringify({...config,proxy:undefined});
    if (key===fingerprint && (!config.enabled || backend)) {publish({config});return;}
    publish({config,status:config.enabled?'starting':'off',reason:'',endpoint:'',token:''});
    await stopBackend();fingerprint=key;
    publish({config,status:config.enabled?'starting':'off',reason:'',endpoint:'',token:''});
    if (!config.enabled) return;
    try {await startBackendInstance();} catch (error) {
      await stopBackend();fingerprint='';
      publish({status:'failed',reason:error.code?`网络组件不可用 (${error.code})`:error.message,endpoint:'',token:''});
    }
  }
  function refresh() {queued=queued.then(apply,apply);return queued;}
  return {
    snapshot:()=>state,
    async start() {
      await refresh();
      if (configPath) {
        try {
          fs.mkdirSync(path.dirname(configPath),{recursive:true});
          watcher=fs.watch(path.dirname(configPath),{persistent:false},(_event,name)=>{
            if (name && String(name)!==path.basename(configPath)) return;
            clearTimeout(debounce);debounce=setTimeout(()=>refresh(),150);debounce.unref?.();
          });
        } catch {log('[app-outbound] 配置监听不可用，下次服务启动时读取');}
      }
    },
    refresh,
    async stop() {
      stopped=true;watcher?.close();clearTimeout(debounce);clearInterval(heartbeat);
      await queued;await stopBackend();bridge.stop();publish({status:'off',reason:'',endpoint:'',token:''});
    },
  };
}
module.exports={createAppOutboundRuntime,validateConfig,networkRuntimeFingerprint};
