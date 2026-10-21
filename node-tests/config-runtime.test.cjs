'use strict';

const test = require('node:test');
const assert = require('node:assert/strict');
const fs = require('node:fs');
const os = require('node:os');
const path = require('node:path');
const configModule = require('../app/src/main/assets/nodejs-project/app-config-runtime.js');

function fixture(options = {}) {
  const home = fs.mkdtempSync(path.join(os.tmpdir(), 'danmu-config-runtime-'));
  const configDir = path.join(home, 'config');
  fs.mkdirSync(configDir, { recursive: true });
  const env = { ...(options.env || {}) };
  const events = [];
  let now = options.now || 10_000;
  let mtime = options.mtime || 1_000;
  const timers = [];
  const watchers = [];
  const fileSystem = Object.create(fs);
  fileSystem.statSync = file => ({ ...fs.statSync(file), mtimeMs: mtime });
  fileSystem.watch = (file, watchOptions, listener) => {
    const watcher = { file, watchOptions, listener, closed: false, close() { this.closed = true; } };
    watchers.push(watcher);
    return watcher;
  };
  const runtime = configModule.createConfigRuntime({
    env,
    fs: fileSystem,
    home,
    ensureDirs: () => fs.mkdirSync(configDir, { recursive: true }),
    getBaseEnvSnapshot: () => ({ TOKEN: 'base', KEEP: 'base-keep' }),
    loadAccessControl: () => events.push('access-control'),
    onReload: async () => { events.push('reload'); },
    log: (...args) => events.push(args.join(' ')),
    setTimeout: (callback, delay) => {
      const timer = { callback, delay, cancelled: false };
      timers.push(timer);
      return timer;
    },
    clearTimeout: timer => { timer.cancelled = true; },
    now: () => now,
  });
  return {
    configDir,
    env,
    events,
    fileSystem,
    home,
    runtime,
    setMtime: value => { mtime = value; },
    advance: value => { now += value; },
    timers,
    watchers,
  };
}

async function runLatestTimer(timers) {
  const timer = [...timers].reverse().find(entry => !entry.cancelled && !entry.ran);
  assert.ok(timer, 'expected a pending timer');
  timer.ran = true;
  await timer.callback();
}

test('loadConfigOnce applies values, restores removed keys and protects app paths', () => {
  const { configDir, env, events, runtime } = fixture({
    env: { TOKEN: 'system', KEEP: 'system-keep', DANMUAPI_LIBDIR: '/protected' },
  });
  try {
    fs.writeFileSync(path.join(configDir, '.env'), 'TOKEN=first\nEXTRA="quote=\\"ok\\" line\\nend"\n');
    runtime.loadConfigOnce();
    assert.equal(env.TOKEN, 'first');
    assert.equal(env.EXTRA, 'quote="ok" line\nend');
    assert.equal(env.KEEP, 'system-keep');
    assert.equal(env.DANMUAPI_LIBDIR, '/protected');

    fs.writeFileSync(path.join(configDir, '.env'), 'TOKEN=second\n');
    runtime.loadConfigOnce();
    assert.equal(env.TOKEN, 'second');
    assert.equal(env.EXTRA, undefined);
    assert.equal(env.KEEP, 'system-keep');
    assert.equal(env.DANMUAPI_LIBDIR, '/protected');
    assert.equal(events.filter(event => event === 'access-control').length, 2);
  } finally {
    fs.rmSync(path.dirname(configDir), { recursive: true, force: true });
  }
});

test('scheduleReload coalesces changes and ignores cancelled timers', async () => {
  const { configDir, env, runtime, timers } = fixture();
  try {
    fs.writeFileSync(path.join(configDir, '.env'), 'TOKEN=one\n');
    runtime.scheduleReload();
    const first = timers.at(-1);
    runtime.scheduleReload();
    const second = timers.at(-1);
    assert.equal(first.cancelled, true);
    assert.equal(second.cancelled, false);
    assert.equal(second.delay, configModule.CONFIG_RELOAD_DELAY_MS);

    await runLatestTimer(timers);
    assert.equal(env.TOKEN, 'one');
    assert.equal(runtime.scheduleReload, runtime.scheduleReload);
    runtime.dispose();
    assert.equal(second.cancelled, false);
  } finally {
    fs.rmSync(path.dirname(configDir), { recursive: true, force: true });
  }
});

test('traffic reload checks are throttled and detect both config files', () => {
  const { configDir, fileSystem, runtime, setMtime, advance } = fixture({ mtime: 1_000 });
  try {
    fileSystem.writeFileSync(path.join(configDir, '.env'), 'TOKEN=one\n');
    fileSystem.writeFileSync(path.join(configDir, 'access-control.json'), '{}');
    runtime.loadConfigOnce();

    assert.equal(runtime.maybeReloadOnTraffic(), false);
    setMtime(2_000);
    fileSystem.writeFileSync(path.join(configDir, 'access-control.json'), '{"mode":"off"}');
    advance(2_000);
    assert.equal(runtime.maybeReloadOnTraffic(), true);
    setMtime(3_000);
    advance(2_000);
    assert.equal(runtime.maybeReloadOnTraffic(), true);
    assert.equal(runtime.maybeReloadOnTraffic(), false);
  } finally {
    runtime.dispose();
    fs.rmSync(path.dirname(configDir), { recursive: true, force: true });
  }
});

test('watch covers the config directory and existing config files', () => {
  const { configDir, runtime, watchers } = fixture();
  try {
    fs.writeFileSync(path.join(configDir, '.env'), 'TOKEN=one\n');
    runtime.watch();
    assert.deepEqual(watchers.map(watcher => path.basename(watcher.file)), ['config', '.env']);
    runtime.dispose();
    assert.deepEqual(watchers.map(watcher => watcher.closed), [true, true]);
  } finally {
    fs.rmSync(path.dirname(configDir), { recursive: true, force: true });
  }
});

test('listen configuration clamps ports and formats IPv6 URLs', () => {
  assert.deepEqual(configModule.readListenConfig({}), {
    host: '0.0.0.0',
    port: 9321,
    proxyPort: 5321,
  });
  assert.deepEqual(configModule.readListenConfig({
    DANMU_API_HOST: '::1',
    DANMU_API_PORT: '70000',
    DANMU_API_PROXY_PORT: '12.9',
  }), {
    host: '::1',
    port: 9321,
    proxyPort: 12,
  });
  assert.deepEqual(configModule.listenOptions('::1', 9321), {
    host: '::1',
    port: 9321,
    ipv6Only: false,
  });
  assert.equal(configModule.listenUrl('::1', 9321, '/__health'), 'http://[::1]:9321/__health');
});

