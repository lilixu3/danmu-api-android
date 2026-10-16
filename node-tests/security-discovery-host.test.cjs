'use strict';
const test = require('node:test');
const assert = require('node:assert/strict');
const fs = require('node:fs');
const path = require('node:path');
const os = require('node:os');
const net = require('node:net');
const { spawn } = require('node:child_process');
const { once } = require('node:events');
const runtimeRoot = path.resolve('app/src/main/assets/nodejs-project');
const pause = ms => new Promise(resolve => setTimeout(resolve, ms));
async function freePort() {
  const server = net.createServer(); server.listen(0, '127.0.0.1'); await once(server, 'listening');
  const port = server.address().port; await new Promise(resolve => server.close(resolve)); return port;
}
for (const worker of [false, true]) test(`security.txt discovery skips core token validation (${worker ? 'Worker' : 'direct'})`, { timeout: 20000 }, async () => {
  const directory = fs.mkdtempSync(path.join(os.tmpdir(), 'danmu-security-host-'));
  let child, output = '';
  try {
    for (const name of fs.readdirSync(runtimeRoot)) if (name.endsWith('.js') || name === 'package.json' || name === 'app-runtime-assets.json') {
      fs.copyFileSync(path.join(runtimeRoot, name), path.join(directory, name));
    }
    fs.symlinkSync(path.join(runtimeRoot, 'node_modules'), path.join(directory, 'node_modules'));
    fs.mkdirSync(path.join(directory, 'config'));
    fs.writeFileSync(path.join(directory, 'config', '.env'), `TOKEN=fixture-token\nDANMU_API_WORKER=${worker}\nDANMU_API_HOT_RELOAD=false\n`);
    const core = path.join(directory, 'danmu_api_stable'); fs.mkdirSync(core);
    fs.writeFileSync(path.join(core, 'package.json'), '{"type":"module"}');
    fs.writeFileSync(path.join(core, 'worker.js'), `export function handleRequest(req, env) {
      const path = new URL(req.url).pathname;
      if (path === '/.well-known/security.txt') console.error('CORE_SECURITY_DISCOVERY_ERROR');
      if (!path.startsWith('/' + env.TOKEN + '/')) return Response.json({errorMessage:'Unauthorized'}, {status:401});
      return Response.json({ok:true});
    }`);
    const port = await freePort();
    child = spawn(process.execPath, [path.join(directory, 'main.js')], {
      cwd: directory, env: { ...process.env, DANMU_API_HOME: directory, DANMU_API_PORT: String(port), DANMU_API_HOST: '127.0.0.1', DANMU_API_PROXY_PORT: String(await freePort()), OUTBOUND_MODE: 'off', DANMU_APP_OUTBOUND_CONFIG: '', PROXY_URL: '' },
      stdio: ['ignore', 'pipe', 'pipe']
    });
    child.stdout.on('data', data => output += data); child.stderr.on('data', data => output += data);
    const base = `http://127.0.0.1:${port}`;
    let ready = false;
    for (let n = 0; n < 100; n++) {
      try { ready = (await fetch(base + '/__health')).ok; } catch {}
      if (ready) break; await pause(100);
    }
    assert.ok(ready, output);
    const discovery = await fetch(base + '/.well-known/security.txt');
    assert.equal(discovery.status, 404); assert.equal(await discovery.text(), 'Not Found');
    const head = await fetch(base + '/.well-known/security.txt', {method:'HEAD'});
    assert.equal(head.status, 404); assert.equal(await head.text(), '');
    assert.equal((await fetch(base + '/fixture-token/api/config')).status, 200);
    assert.equal((await fetch(base + '/wrong-token/api/config')).status, 401);
    assert.equal((await fetch(base + '/.well-known/other.txt')).status, 401);
    assert.equal((await fetch(base + '/wrong-token/.well-known/security.txt')).status, 401);
    assert.doesNotMatch(output, /CORE_SECURITY_DISCOVERY_ERROR/);
  } finally {
    if (child && child.exitCode === null) { child.kill('SIGTERM'); await Promise.race([once(child, 'exit'), pause(2000)]); if (child.exitCode === null) { child.kill('SIGKILL'); await once(child, 'exit'); } }
    fs.rmSync(directory, { recursive: true, force: true });
  }
});
