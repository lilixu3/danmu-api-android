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
for (const worker of [false, true]) test(`shared configuration API hot updates and browser deep links (${worker ? 'Worker' : 'direct'})`, { timeout: 25000 }, async () => {
  const directory = fs.mkdtempSync(path.join(os.tmpdir(), 'danmu-management-host-'));
  let child, output = '';
  try {
    for (const name of fs.readdirSync(runtimeRoot)) if (name.endsWith('.js') || name === 'package.json') {
      fs.copyFileSync(path.join(runtimeRoot, name), path.join(directory, name));
    }
    fs.symlinkSync(path.join(runtimeRoot, 'node_modules'), path.join(directory, 'node_modules'));
    fs.mkdirSync(path.join(directory, 'config'));
    const envFile = path.join(directory, 'config', '.env');
    fs.writeFileSync(envFile, `TOKEN=user\nADMIN_TOKEN=administrator\nDANMU_API_WORKER=${worker}\nDANMU_API_HOT_RELOAD=false\nSOURCE_ORDER=old\n`);
    const core = path.join(directory, 'danmu_api_stable'); fs.mkdirSync(core);
    fs.writeFileSync(path.join(core, 'package.json'), '{"type":"module"}');
    fs.writeFileSync(path.join(core, 'worker.js'), `export function handleRequest(req, env) {
      const url = new URL(req.url);
      if (url.pathname.endsWith('/api/config')) return Response.json({originalEnvVars: {SOURCE_ORDER:env.SOURCE_ORDER, ADMIN_TOKEN:env.ADMIN_TOKEN}});
      return new Response('<html><body>core settings</body></html>', {headers:{'content-type':'text/html'}});
    }`);
    const port = await freePort();
    child = spawn(process.execPath, [path.join(directory, 'main.js')], {
      cwd: directory, env: { ...process.env, DANMU_API_HOME: directory, DANMU_API_PORT: String(port), DANMU_API_HOST: '127.0.0.1', DANMU_API_PROXY_PORT: String(await freePort()), OUTBOUND_MODE: 'off', DANMU_APP_OUTBOUND_CONFIG: '', DANMU_APP_OUTBOUND_HELPER: '', PROXY_URL: '' },
      stdio: ['ignore', 'pipe', 'pipe']
    });
    child.stdout.on('data', data => output += data); child.stderr.on('data', data => output += data);
    const base = `http://127.0.0.1:${port}`;
    let ready = false;
    for (let n = 0; n < 150; n++) {
      try { ready = (await fetch(base + '/__health')).ok; } catch {}
      if (ready) break; await pause(100);
    }
    assert.ok(ready, output);
    async function set(token, key, value, origin) {
      return fetch(base + '/' + token + '/api/env/set', { method:'POST', headers:{'content-type':'application/json', ...(origin ? {origin} : {})}, body: JSON.stringify({key, value}) });
    }
    assert.equal((await set('user', 'SOURCE_ORDER', 'evil')).status, 403);
    assert.equal((await set('administrator', 'SOURCE_ORDER', 'evil', 'https://evil.invalid')).status, 403);
    assert.equal((await set('administrator', '../evil', 'value')).status, 400);
    const saved = await set('administrator', 'SOURCE_ORDER', 'bahamut,tmdb', base.replace('http:', 'https:'));
    assert.equal(saved.status, 200); assert.equal((await saved.json()).success, true);
    assert.match(fs.readFileSync(envFile, 'utf8'), /SOURCE_ORDER="bahamut,tmdb"/);
    const config = await (await fetch(base + '/administrator/api/config')).json();
    assert.equal(config.originalEnvVars.SOURCE_ORDER, 'bahamut,tmdb');
    assert.equal(child.exitCode, null);
    const html = await (await fetch(base + '/administrator?app_section=env')).text();
    assert.match(html, /switchSection\('env'\)/);
    assert.doesNotMatch(html, /administrator/);
    const normal = await (await fetch(base + '/user')).text();
    assert.doesNotMatch(normal, /data-danmu-app-section/);
    const removed = await fetch(base + '/administrator/api/env/del', {method:'POST', headers:{'content-type':'application/json'}, body: JSON.stringify({key:'SOURCE_ORDER'})});
    assert.equal((await removed.json()).success, true);
    assert.doesNotMatch(fs.readFileSync(envFile, 'utf8'), /SOURCE_ORDER/);
  } finally {
    if (child && child.exitCode === null) { child.kill('SIGTERM'); await Promise.race([once(child, 'exit'), pause(2000)]); if (child.exitCode === null) { child.kill('SIGKILL'); await once(child, 'exit'); } }
    fs.rmSync(directory, { recursive: true, force: true });
  }
});
