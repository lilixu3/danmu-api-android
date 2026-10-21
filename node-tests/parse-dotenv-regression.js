const path = require('path');

const { parseDotEnv } = require(path.join(
  __dirname,
  '../app/src/main/assets/nodejs-project/app-config-runtime.js'
));

const cases = [
  {
    name: 'plain runtime values',
    input: 'TOKEN=abc123\nIP_BLACKLIST=127.0.0.1,10.0.0.0/8\n',
    expected: {
      TOKEN: 'abc123',
      IP_BLACKLIST: '127.0.0.1,10.0.0.0/8',
    },
  },
  {
    name: 'quoted url with hash and equals',
    input: 'PROXY_URL="https://host/path?a=1#frag"\n',
    expected: {
      PROXY_URL: 'https://host/path?a=1#frag',
    },
  },
  {
    name: 'blocked words regex keeps common regex escapes',
    input: 'BLOCKED_WORDS="/\\d+[^\\w\\d\\s]+/,/[@#&$%^*+\\|/\\-_=<>]/"\n',
    expected: {
      BLOCKED_WORDS: '/\\d+[^\\w\\d\\s]+/,/[@#&$%^*+\\|/\\-_=<>]/',
    },
  },
  {
    name: 'dotenv escape sequences are decoded intentionally',
    input: 'VALUE="quote=\\"ok\\" path\\\\segment line\\nend"\n',
    expected: {
      VALUE: 'quote="ok" path\\segment line\nend',
    },
  },
];

for (const item of cases) {
  const actual = parseDotEnv(item.input);
  for (const [key, expected] of Object.entries(item.expected)) {
    if (actual[key] !== expected) {
      console.error(`${item.name}: ${key} mismatch`);
      console.error('expected:', JSON.stringify(expected));
      console.error('actual  :', JSON.stringify(actual[key]));
      process.exit(1);
    }
  }
}

console.log(`parseDotEnv regression passed (${cases.length} cases)`);
