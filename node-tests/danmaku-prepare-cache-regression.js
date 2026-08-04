const fs = require('fs');
const path = require('path');
const vm = require('vm');

const root = path.resolve(__dirname, '..');
const serverPath = path.join(root, 'app/src/main/assets/nodejs-project/android-server.js');
const source = fs.readFileSync(serverPath, 'utf8');
const start = source.indexOf('function _countDanmakuXml');
const end = source.indexOf('\nfunction _preparedDanmakuError', start);

if (start < 0 || end < 0) {
  throw new Error('danmaku prepare helpers not found');
}

const helpers = vm.runInNewContext(
  `${source.slice(start, end)}\n({ _countDanmakuXml, _looksLikeDanmakuXml });`
);

const xml = '<?xml version="1.0"?><i><d p="1,1,25,1">a</d><d>b</d><data>ignored</data></i>';
if (helpers._countDanmakuXml(xml) !== 2) {
  throw new Error('danmaku XML count mismatch');
}
if (helpers._countDanmakuXml('<i><d\np="1">a</d></i>') !== 1) {
  throw new Error('whitespace danmaku element was not counted');
}
if (helpers._countDanmakuXml('<i></i>') !== 0) {
  throw new Error('empty danmaku XML must count as zero');
}
if (!helpers._looksLikeDanmakuXml('application/xml', '<i></i>')) {
  throw new Error('valid empty XML was rejected');
}
if (helpers._looksLikeDanmakuXml('application/json', '{"comments":[]}')) {
  throw new Error('JSON must not be accepted as prepared XML');
}

for (const marker of [
  "strippedPathname === '/__danmaku/prepare'",
  "strippedPathname.startsWith('/__danmaku/prepared/')",
  "pathname !== '/api/v2/comment'",
  '_PREPARED_DANMAKU_MAX_TOTAL_BYTES',
]) {
  if (!source.includes(marker)) throw new Error(`missing prepare cache contract: ${marker}`);
}

console.log('danmaku prepare cache regression passed');
