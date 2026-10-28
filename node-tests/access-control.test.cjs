'use strict';

const test = require('node:test');
const assert = require('node:assert/strict');
const fs = require('node:fs');
const os = require('node:os');
const path = require('node:path');
const {
  createAccessControl,
  isLoopbackIp,
  normalizeAccessMode,
  normalizeClientIp,
  normalizeIpList,
  readClientIp,
} = require('../app/src/main/assets/nodejs-project/app-access-control.js');

function fixture(options = {}) {
  const home = fs.mkdtempSync(path.join(os.tmpdir(), 'danmu-access-control-'));
  const file = path.join(home, 'config', 'access-control.json');
  const responses = [];
  const control = createAccessControl({
    fs,
    file,
    ensureDirs: () => fs.mkdirSync(path.dirname(file), { recursive: true }),
    stripTokenPrefix: pathname => pathname.startsWith('/token/') ? pathname.slice('/token'.length) : pathname,
    isAdminAuthorized: options.isAdminAuthorized || (() => true),
    sendJson: (res, statusCode, payload) => {
      responses.push({ statusCode, payload });
      res.statusCode = statusCode;
      res.payload = payload;
    },
    readJsonBody: options.readJsonBody || (async () => ({})),
    log: () => {},
    now: options.now || (() => 1_700_000_000_000),
  });
  return { home, file, responses, control };
}

function response() {
  return { statusCode: 0, payload: null, setHeader() {}, end() {} };
}

test('IP parsing handles forwarded lists, ports, brackets, mapped addresses and invalid input', () => {
  assert.equal(normalizeClientIp(' 203.0.113.7, 10.0.0.1 '), '203.0.113.7');
  assert.equal(normalizeClientIp('203.0.113.7:443'), '203.0.113.7');
  assert.equal(normalizeClientIp('[fd00::1]:9321'), 'fd00::1');
  assert.equal(normalizeClientIp('fe80::1%wlan0'), 'fe80::1');
  assert.equal(normalizeClientIp('::ffff:198.51.100.9'), '198.51.100.9');
  assert.equal(normalizeClientIp('not-an-ip'), '');
  assert.equal(normalizeClientIp('', ''), '');
  assert.deepEqual(normalizeIpList(['203.0.113.7', '203.0.113.7:80', 'bad', '198.51.100.2']), ['203.0.113.7', '198.51.100.2']);
  assert.deepEqual(normalizeIpList('203.0.113.7, 198.51.100.2;203.0.113.7'), ['203.0.113.7', '198.51.100.2']);
});

test('loopback and access mode aliases preserve current app contract', () => {
  for (const ip of ['127.0.0.1', '127.8.9.10', '::1', '::ffff:127.0.0.1']) assert.equal(isLoopbackIp(ip), true, ip);
  for (const ip of ['192.0.2.1', 'fd00::1', 'not-an-ip', '']) assert.equal(isLoopbackIp(ip), false, ip);
  for (const raw of ['blacklist', 'black', 'BLOCK', 'deny']) assert.equal(normalizeAccessMode(raw), 'blacklist');
  for (const raw of ['whitelist', 'white', 'allow', 'off', 'none', 'disabled']) assert.equal(normalizeAccessMode(raw), 'off');
  assert.equal(normalizeAccessMode('unknown', 'blacklist'), 'blacklist');
});

test('forwarded client IP is preferred over socket address', () => {
  const request = { headers: { 'x-forwarded-for': '203.0.113.8, 10.0.0.1' }, socket: { remoteAddress: '198.51.100.1' } };
  assert.equal(readClientIp(request), '203.0.113.8');
  assert.equal(readClientIp({ headers: { 'x-real-ip': '198.51.100.2' }, socket: { remoteAddress: '127.0.0.1' } }), '198.51.100.2');
  assert.equal(readClientIp({ headers: {}, socket: { remoteAddress: '::ffff:198.51.100.3' } }), '198.51.100.3');
  assert.equal(readClientIp({ headers: {}, socket: {} }), '');
});

test('internal, loopback, off and blacklist decisions stay compatible', () => {
  const { home, control } = fixture();
  try {
    assert.deepEqual(control.evaluate('203.0.113.7', '/token/__health'), { allowed: true, reason: 'internal', clientIp: '203.0.113.7' });
    assert.deepEqual(control.evaluate('127.0.0.1', '/api/v2/search/anime'), { allowed: true, reason: 'loopback', clientIp: '127.0.0.1' });
    assert.deepEqual(control.evaluate('203.0.113.7', '/api/v2/search/anime'), { allowed: true, reason: 'mode_off', clientIp: '203.0.113.7' });
    control.apply({ mode: 'blacklist', blacklist: ['203.0.113.7'], updatedAtMs: 1 });
    assert.deepEqual(control.evaluate('203.0.113.7', '/api/v2/search/anime'), { allowed: false, reason: 'blacklist_hit', clientIp: '203.0.113.7' });
    assert.deepEqual(control.evaluate('203.0.113.7', '/token/__access-control'), { allowed: true, reason: 'internal', clientIp: '203.0.113.7' });
    assert.deepEqual(control.evaluate('198.51.100.2', '/api/v2/search/anime'), { allowed: true, reason: 'blacklist_pass', clientIp: '198.51.100.2' });
  } finally {
    fs.rmSync(home, { recursive: true, force: true });
  }
});

test('device tracking excludes loopback, caps size and reports newest devices first', () => {
  let timestamp = 1_700_000_000_000;
  const { home, control } = fixture({ now: () => timestamp++ });
  try {
    control.recordDeviceAccess({ clientIp: '127.0.0.1', method: 'GET', pathname: '/ignored', allowed: true, reason: 'loopback' });
    control.recordDeviceAccess({ clientIp: '203.0.113.7', method: 'post', pathname: '/api/a', allowed: true, reason: 'mode_off', userAgent: 'UA-1' });
    control.recordDeviceAccess({ clientIp: '203.0.113.8', method: 'GET', pathname: '/api/b', allowed: false, reason: 'blacklist_hit', userAgent: 'UA-2' });
    control.apply({ mode: 'blacklist', blacklist: ['203.0.113.8'], updatedAtMs: 1 });

    const payload = control.buildPayload();
    assert.equal(payload.stats.trackedDevices, 2);
    assert.equal(payload.stats.totalAllowedRequests, 1);
    assert.equal(payload.stats.totalBlockedRequests, 1);
    assert.deepEqual(payload.devices.map(device => device.ip), ['203.0.113.8', '203.0.113.7']);
    assert.equal(payload.devices[0].effectiveBlocked, true);
    assert.equal(payload.devices[0].lastMethod, 'GET');
    assert.equal(payload.devices[1].lastUserAgent, 'UA-1');

    for (let index = 0; index < 250; index++) {
      control.recordDeviceAccess({ clientIp: `198.51.${Math.floor(index / 250)}.${index % 250 + 1}`, method: 'GET', pathname: '/api/x', allowed: true, reason: 'mode_off' });
    }
    assert.equal(control.stats().trackedDevices, 240);
    control.clearDevices();
    assert.equal(control.stats().trackedDevices, 0);
  } finally {
    fs.rmSync(home, { recursive: true, force: true });
  }
});

test('endpoint persists normalized rules and clears devices or rules when requested', async () => {
  let body = { mode: 'blacklist', blacklist: ['203.0.113.7', '203.0.113.7:80'] };
  const { home, file, responses, control } = fixture({ readJsonBody: async () => body });
  try {
    control.recordDeviceAccess({ clientIp: '203.0.113.7', method: 'GET', pathname: '/api/a', allowed: true, reason: 'mode_off' });
    const first = response();
    await control.handleEndpoint({ method: 'POST' }, first, new URL('http://localhost/__access-control'), '127.0.0.1');
    assert.equal(first.statusCode, 200);
    assert.deepEqual(first.payload.config.blacklist, ['203.0.113.7']);
    assert.deepEqual(JSON.parse(fs.readFileSync(file, 'utf8')).blacklist, ['203.0.113.7']);

    body = { clearDevices: true };
    const second = response();
    await control.handleEndpoint({ method: 'PATCH' }, second, new URL('http://localhost/__access-control'), '127.0.0.1');
    assert.equal(second.payload.stats.trackedDevices, 0);
    assert.deepEqual(second.payload.config.blacklist, ['203.0.113.7']);

    body = { clearRules: true };
    const third = response();
    await control.handleEndpoint({ method: 'PUT' }, third, new URL('http://localhost/__access-control'), '127.0.0.1');
    assert.deepEqual(third.payload.config.blacklist, []);
    assert.equal(responses.length, 3);
  } finally {
    fs.rmSync(home, { recursive: true, force: true });
  }
});

test('endpoint rejects invalid methods, unauthorized callers and invalid bodies', async () => {
  const denied = fixture({ isAdminAuthorized: () => false });
  try {
    const responseRef = response();
    await denied.control.handleEndpoint({ method: 'GET' }, responseRef, new URL('http://localhost/__access-control'), '203.0.113.7');
    assert.equal(responseRef.statusCode, 403);
    assert.equal(responseRef.payload.errorMessage, 'Forbidden');
  } finally {
    fs.rmSync(denied.home, { recursive: true, force: true });
  }

  const invalidMethod = fixture();
  try {
    const responseRef = response();
    await invalidMethod.control.handleEndpoint({ method: 'DELETE' }, responseRef, new URL('http://localhost/__access-control'), '127.0.0.1');
    assert.equal(responseRef.statusCode, 405);
  } finally {
    fs.rmSync(invalidMethod.home, { recursive: true, force: true });
  }

  const invalidBody = fixture({ readJsonBody: async () => ({ mode: 'unsupported' }) });
  try {
    const responseRef = response();
    await invalidBody.control.handleEndpoint({ method: 'POST' }, responseRef, new URL('http://localhost/__access-control'), '127.0.0.1');
    assert.equal(responseRef.statusCode, 400);
    assert.match(responseRef.payload.errorMessage, /off \/ blacklist/);
  } finally {
    fs.rmSync(invalidBody.home, { recursive: true, force: true });
  }
});

test('loading malformed or missing files does not throw or execute outside the file', () => {
  const missing = fixture();
  try {
    assert.doesNotThrow(() => missing.control.loadFromDisk());
    assert.equal(missing.control.config().mode, 'off');
    fs.mkdirSync(path.dirname(missing.file), { recursive: true });
    fs.writeFileSync(missing.file, '{bad json');
    assert.doesNotThrow(() => missing.control.loadFromDisk());
    fs.writeFileSync(missing.file, JSON.stringify({ mode: 'blacklist', blacklist: ['203.0.113.7'] }));
    missing.control.loadFromDisk();
    assert.equal(missing.control.config().mode, 'blacklist');
    assert.equal(missing.control.config().blacklistCount, 1);
  } finally {
    fs.rmSync(missing.home, { recursive: true, force: true });
  }
});
