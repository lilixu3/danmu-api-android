const helpers = require('../app/src/main/assets/nodejs-project/app-danmaku-cache.js');

const xml = '<?xml version="1.0"?><i><d p="1,1,25,1">a</d><d>b</d><data>ignored</data></i>';
if (helpers.countDanmakuXml(xml) !== 2) {
  throw new Error('danmaku XML count mismatch');
}
if (helpers.countDanmakuXml('<i><d\np="1">a</d></i>') !== 1) {
  throw new Error('whitespace danmaku element was not counted');
}
if (helpers.countDanmakuXml('<i></i>') !== 0) {
  throw new Error('empty danmaku XML must count as zero');
}
if (!helpers.looksLikeDanmakuXml('application/xml', '<i></i>')) {
  throw new Error('valid empty XML was rejected');
}
if (helpers.looksLikeDanmakuXml('application/json', '{"comments":[]}')) {
  throw new Error('JSON must not be accepted as prepared XML');
}

console.log('danmaku prepare cache regression passed');
