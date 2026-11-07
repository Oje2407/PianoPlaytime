// Copies the shared app (../www) into this folder so the desktop build packs the same app as the phones.
const fs = require('fs');
const path = require('path');
const src = path.join(__dirname, '..', '..', 'www');
const dst = path.join(__dirname, '..', 'www');
fs.rmSync(dst, { recursive: true, force: true });
fs.cpSync(src, dst, { recursive: true });
console.log('Copied app from', src);
