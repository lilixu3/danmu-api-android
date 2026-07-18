import { brotliCompressSync } from 'node:zlib';
import brotliDecompress from '../app/src/main/assets/nodejs-project/node_modules/brotli/decompress.js';

const expected = 'danmu-api-android-brotli-smoke';
const compressed = brotliCompressSync(Buffer.from(expected, 'utf8'));
const restored = Buffer.from(brotliDecompress(compressed)).toString('utf8');

if (restored !== expected) {
  throw new Error(`Brotli 往返校验失败：${restored}`);
}

console.log('Brotli runtime smoke: OK');
