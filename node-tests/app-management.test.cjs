const test = require('node:test');
const assert = require('node:assert/strict');
const fs = require('fs');
const os = require('os');
const path = require('path');
const { Readable } = require('stream');
const api = require('../app/src/main/assets/nodejs-project/app-management.js');

test('admin route requires exact secret and known mutation action', () => {
  assert.equal(api.routeForAdmin('/admin/api/env/set', 'admin'), 'set');
  for (const pathname of ['/user/api/env/set', '/api/env/set', '/admin-evil/api/env/set', '/admin/api/env/set/extra']) {
    assert.equal(api.routeForAdmin(pathname, 'admin'), null);
  }
  assert.equal(api.routeForAdmin('//api/env/set', ''), null);
});
test('origin validation supports TLS termination while rejecting other hosts and ports', () => {
  for (const [origin, host] of [
    ['', '127.0.0.1:9321'], ['http://127.0.0.1:9321', '127.0.0.1:9321'],
    ['https://api.example.test', 'api.example.test'], ['https://api.example.test', 'api.example.test:443'],
    ['https://[fd00::1]:9321', '[fd00::1]:9321']
  ]) assert.equal(api.isManagementOriginAllowed(origin, host), true);
  for (const origin of ['null', 'https://evil.invalid', 'https://api.example.test:9443',
    'file://api.example.test', 'https://admin@api.example.test', 'https://api.example.test/path']) {
    assert.equal(api.isManagementOriginAllowed(origin, 'api.example.test'), false);
  }
});
test('updates one shared config, collapses duplicates and preserves unrelated comments/keys', () => {
  const out = api.updateEnvContent('# comment\nA=old\nexport A=duplicate\nB=kept\n', 'A', 'new');
  assert.equal(out, '# comment\nA="new"\nB=kept\n');
  assert.equal(api.updateEnvContent(out, 'A', undefined, true), '# comment\nB=kept\n');
});
test('quotes special characters and blocks line injection and traversal', () => {
  const value = 'a#b"c\\d\nEVIL=true';
  const out = api.updateEnvContent('', 'KEY', value);
  assert.equal(out.trim(), 'KEY=' + JSON.stringify(value));
  assert.equal(out.split('\n').length, 2);
  for (const key of ['../../x', 'A\nEVIL', '__proto__', '']) assert.throws(() => api.updateEnvContent('', key, 'value'));
});
test('atomic save writes the supplied shared file and leaves other files intact', () => {
  const dir = fs.mkdtempSync(path.join(os.tmpdir(), 'danmu-management-'));
  try {
    const file = path.join(dir, 'config', '.env');
    api.saveEnvValue(file, 'SOURCE_ORDER', 'bahamut,tmdb');
    api.saveEnvValue(file, 'ADMIN_TOKEN', 'administrator');
    assert.match(fs.readFileSync(file, 'utf8'), /SOURCE_ORDER="bahamut,tmdb"/);
    assert.deepEqual(fs.readdirSync(path.dirname(file)), ['.env']);
    api.saveEnvValue(file, 'ADMIN_TOKEN', undefined, true);
    assert.doesNotMatch(fs.readFileSync(file, 'utf8'), /ADMIN_TOKEN/);
  } finally { fs.rmSync(dir, { recursive: true, force: true }); }
});
test('bounds request size and handles valid or malformed JSON', async () => {
  assert.deepEqual(await api.readManagementBody(Readable.from([Buffer.from('{"key":"A","value":"b"}') ])), { key: 'A', value: 'b' });
  await assert.rejects(api.readManagementBody(Readable.from([Buffer.alloc(256 * 1024 + 1)])), { statusCode: 413 });
  await assert.rejects(api.readManagementBody(Readable.from([Buffer.from('{bad') ])));
});
test('frontend deep link uses only approved sections; leaves arbitrary payloads and non-HTML unchanged', () => {
  const html = '<html><body>core</body></html>';
  assert.match(api.injectWebSection(html, 'env'), /switchSection\('env'\)/);
  assert.match(api.injectWebSection(html, 'logs'), /switchSection\('logs'\)/);
  assert.equal(api.injectWebSection(html, "env');evil();('"), html);
  assert.equal(api.injectWebSection('plain response', 'env'), 'plain response');
});
