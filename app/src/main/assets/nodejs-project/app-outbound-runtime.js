'use strict';
const fs = require('node:fs');
const path = require('node:path');
const { spawn } = require('node:child_process');
const { randomBytes } = require('node:crypto');
const { installBridge } = require('./app-outbound-bridge.js');

function validateConfig(input = {}) {
  const config = {
    enabled: input.enabled === true,
    sources: Array.isArray(input.sources) ? [...new Set(input.sources)] : ['bahamut','tmdb'],
    httpVersion: String(input.httpVersion || 'auto'),
    dohUrl: String(input.dohUrl || '').trim(),
    connectTimeoutMs: Number(input.connectTimeoutMs ?? 3000),
  };
  if (config.sources.some(source => !['bahamut','tmdb','dandan','animeko'].includes(source))) throw new Error('不支持的增强直连来源');
  if (!['auto','h2','h3'].includes(config.httpVersion)) throw new Error('协议必须为 auto、h2 或 h3');
  if (!Number.isInteger(config.connectTimeoutMs) || config.connectTimeoutMs < 1 || config.connectTimeoutMs > 60000) throw new Error('连接超时必须在 1–60000 毫秒之间');
  if (config.dohUrl) {
    const url = new URL(config.dohUrl);
    if (url.protocol !== 'https:' || url.username || url.password || url.hash) throw new Error('DoH 必须为不含凭据的 HTTPS 地址');
  }
  return config;
}
function createAppOutboundRuntime({ configPath = process.env.DANMU_APP_OUTBOUND_CONFIG, helperPath = process.env.DANMU_APP_OUTBOUND_HELPER, onSnapshot = () => {}, log = console.log } = {}) {
  let state = {status:'off', config:validateConfig(), reason:''};
  let child = null, watcher = null, debounce = null, heartbeat = null, stopped = false, queued = Promise.resolve(), fingerprint = '';
  const bridge = installBridge();
  const statusPath = configPath ? path.join(path.dirname(configPath),'status.json') : '';
  // Heartbeats only touch the local status file; they must not broadcast a
  // complete env snapshot to every Worker when routing has not changed.
  function writeStatus() {
    if (statusPath) {
      const output = {status:state.status, reason:state.reason || '', pid:process.pid, heartbeat:Date.now(), httpVersion:state.config.httpVersion, sources:state.config.sources};
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
  async function stopChild() {
    const previous = child; child = null;
    if (!previous || previous.exitCode !== null || previous.signalCode !== null) return;
    await new Promise(resolve => {
      const timer = setTimeout(() => { previous.kill('SIGKILL'); resolve(); },1500); timer.unref?.();
      previous.once('exit',()=>{clearTimeout(timer);resolve();});
      previous.stdin.end(); previous.kill('SIGTERM');
    });
  }
  async function startChild() {
    const config = state.config;
    if (!helperPath || !path.isAbsolute(helperPath)) throw new Error('App 未提供网络组件，请更新或重新安装完整 APK');
    fs.accessSync(helperPath,fs.constants.X_OK);
    const token = randomBytes(32).toString('hex');
    await new Promise((resolve,reject) => {
      const instance = spawn(helperPath,['--http-version',config.httpVersion,'--connect-timeout-ms',String(config.connectTimeoutMs),'--doh-url',config.dohUrl],{
        stdio:['pipe','pipe','pipe'], env:{...process.env,DANMU_OUTBOUND_TOKEN:token},
      });
      child = instance;
      let settled=false, output='';
      const finish = error => {
        if (settled) return; settled=true; clearTimeout(timer);
        error ? reject(error) : resolve();
      };
      const timer = setTimeout(()=>finish(new Error('增强直连组件启动超时')),5000); timer.unref?.();
      instance.stdout.on('data',chunk=>{
        output += chunk.toString();
        if (output.length>4096) {finish(new Error('网络组件返回异常'));return;}
        const newline=output.indexOf('\n');if(newline<0)return;
        try {
          const ready=JSON.parse(output.slice(0,newline));
          const url=new URL(ready.url);
          if (!ready.ready || ready.appProtocol !== 1 || url.protocol!=='http:' || url.hostname!=='127.0.0.1' || !url.port || url.pathname!=='/' || url.username || url.password || url.search || url.hash) throw new Error('invalid endpoint');
          publish({status:'ready',reason:'',endpoint:url.origin,token});finish();
        } catch {finish(new Error('网络组件握手失败'));}
      });
      instance.stderr.on('data',chunk=>{
        // Go logs only host/protocol/status, never URLs, auth headers or bodies.
        for (const line of String(chunk).trim().split('\n')) if (line) log('[app-outbound]',line);
      });
      instance.once('error',error=>finish(new Error(`无法启动网络组件 (${error.code || 'spawn'})`)));
      instance.once('exit',()=>{
        finish(new Error('网络组件提前退出'));
        if (child !== instance) return;
        child=null;fingerprint='';
        publish({status:stopped?'off':'failed',reason:stopped?'':'网络组件退出，将尝试恢复',endpoint:'',token:''});
        if (!stopped && state.config.enabled) {clearTimeout(debounce);debounce=setTimeout(()=>refresh(),2000);debounce.unref?.();}
      });
    });
  }
  async function apply() {
    if (stopped) return;
    let config;
    try {
      config=validateConfig(configPath && fs.existsSync(configPath) ? JSON.parse(fs.readFileSync(configPath,'utf8')) : {});
    } catch (error) {
      await stopChild();fingerprint='';
      publish({status:'failed',reason:`增强直连配置无效：${error.message}`,endpoint:'',token:''});return;
    }
    const key=JSON.stringify(config);
    if (key===fingerprint && (!config.enabled || child)) {publish();return;}
    publish({config,status:config.enabled?'starting':'off',reason:'',endpoint:'',token:''});
    await stopChild();fingerprint=key;
    publish({config,status:config.enabled?'starting':'off',reason:'',endpoint:'',token:''});
    if (!config.enabled) return;
    try {await startChild();} catch (error) {
      await stopChild();fingerprint='';
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
      await queued;await stopChild();bridge.stop();publish({status:'off',reason:'',endpoint:'',token:''});
    },
  };
}
module.exports={createAppOutboundRuntime,validateConfig};
