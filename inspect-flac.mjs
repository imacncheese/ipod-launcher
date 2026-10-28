import { readFileSync } from 'node:fs';

const path = process.argv[2];
const buf = readFileSync(path);
if (buf.toString('latin1', 0, 4) !== 'fLaC') {
  console.log('not a FLAC');
  process.exit(1);
}

const names = {
  0: 'STREAMINFO',
  1: 'PADDING',
  2: 'APPLICATION',
  3: 'SEEKTABLE',
  4: 'VORBIS_COMMENT',
  5: 'CUESHEET',
  6: 'PICTURE',
};

let pos = 4;
const blocks = [];
while (true) {
  const h = buf[pos++];
  const last = (h & 0x80) !== 0;
  const type = h & 0x7f;
  const len = (buf[pos] << 16) | (buf[pos + 1] << 8) | buf[pos + 2];
  pos += 3;
  const start = pos;
  blocks.push({ type, len });

  if (type === 4) {
    let p = start;
    const vlen = buf.readUInt32LE(p);
    p += 4;
    const vendor = buf.toString('utf8', p, p + vlen);
    p += vlen;
    const count = buf.readUInt32LE(p);
    p += 4;
    const keys = [];
    for (let i = 0; i < count; i++) {
      const clen = buf.readUInt32LE(p);
      p += 4;
      const entry = buf.toString('utf8', p, p + clen);
      p += clen;
      const eq = entry.indexOf('=');
      keys.push(eq > 0 ? entry.slice(0, eq) : entry);
    }
    console.log(`VORBIS vendor="${vendor}" (${count} comments)`);
    console.log('  keys: ' + (keys.length ? keys.join(', ') : '(none)'));
  }

  if (type === 6) {
    let p = start + 4; // picture type
    const mimeLen = buf.readUInt32BE(p);
    p += 4;
    const mime = buf.toString('latin1', p, p + mimeLen);
    p += mimeLen;
    const descLen = buf.readUInt32BE(p);
    p += 4 + descLen;
    const w = buf.readUInt32BE(p);
    const hgt = buf.readUInt32BE(p + 4);
    console.log(`  PICTURE ${mime} ${w}x${hgt}`);
  }

  pos = start + len;
  if (last) break;
  if (blocks.length > 40) break;
}

console.log('blocks: ' + blocks.map((b) => `${names[b.type] || b.type}(${b.len})`).join(' '));
console.log('audio starts at byte ' + pos);
