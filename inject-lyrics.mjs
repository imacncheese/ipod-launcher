import { readFileSync, writeFileSync } from 'node:fs';

const src = process.argv[2];
const dst = process.argv[3];

const buf = readFileSync(src);
if (buf.toString('latin1', 0, 4) !== 'fLaC') throw new Error('not a FLAC');

let pos = 4;
const blocks = [];
let audioStart = 0;
while (true) {
  const h = buf[pos++];
  const last = (h & 0x80) !== 0;
  const type = h & 0x7f;
  const len = (buf[pos] << 16) | (buf[pos + 1] << 8) | buf[pos + 2];
  pos += 3;
  blocks.push({ type, data: buf.subarray(pos, pos + len) });
  pos += len;
  if (last) {
    audioStart = pos;
    break;
  }
}

const lyrics = [
  'These are test lyrics',
  'embedded in the FLAC file',
  'so the iPod lyrics screen',
  'has something to display',
  '',
  'Line six follows a blank line',
  'and the wheel scrolls down',
  'through the rest of the text',
  '',
  'Wrapping should keep long lines inside the panel width instead of running off',
  '',
  'Nine',
  'Ten',
  'Eleven',
  'Twelve',
  'Thirteen',
  'Fourteen',
  'Fifteen',
  'Sixteen',
  'Seventeen',
  'Eighteen',
  'Nineteen',
  'Twenty',
].join('\n');

let delta = 0;
for (const b of blocks) {
  if (b.type !== 4) continue;

  let p = 0;
  const vlen = b.data.readUInt32LE(p);
  p += 4;
  const vendor = b.data.subarray(p, p + vlen);
  p += vlen;
  const count = b.data.readUInt32LE(p);
  p += 4;
  const comments = [];
  for (let i = 0; i < count; i++) {
    const clen = b.data.readUInt32LE(p);
    p += 4;
    comments.push(b.data.subarray(p, p + clen));
    p += clen;
  }

  const rewritten = comments.map((c) => {
    const s = c.toString('utf8');
    const eq = s.indexOf('=');
    if (eq <= 0) return c;
    const key = s.slice(0, eq).toLowerCase();
    if (key === 'title') return Buffer.from('TITLE=A - Lyrics Test', 'utf8');
    if (key === 'artist') return Buffer.from('ARTIST=Test Artist', 'utf8');
    if (key === 'album') return Buffer.from('ALBUM=Launcher Test', 'utf8');
    return c;
  });
  rewritten.push(Buffer.from('LYRICS=' + lyrics, 'utf8'));

  const parts = [];
  const lenBuf = Buffer.alloc(4);
  lenBuf.writeUInt32LE(vendor.length, 0);
  parts.push(lenBuf, vendor);
  const cntBuf = Buffer.alloc(4);
  cntBuf.writeUInt32LE(rewritten.length, 0);
  parts.push(cntBuf);
  for (const c of rewritten) {
    const cb = Buffer.alloc(4);
    cb.writeUInt32LE(c.length, 0);
    parts.push(cb, c);
  }
  const next = Buffer.concat(parts);
  delta = next.length - b.data.length;
  b.data = next;
}

// Absorb the growth into the trailing PADDING so the metadata length, the audio
// offset and the total file size stay identical to the source file.
if (delta !== 0) {
  const pad = blocks.find((b) => b.type === 1);
  if (!pad || pad.data.length < delta) throw new Error('no room in padding');
  pad.data = Buffer.alloc(pad.data.length - delta);
}

const out = [Buffer.from('fLaC', 'latin1')];
for (let i = 0; i < blocks.length; i++) {
  const b = blocks[i];
  const header = Buffer.alloc(4);
  header[0] = (i === blocks.length - 1 ? 0x80 : 0x00) | b.type;
  header[1] = (b.data.length >> 16) & 0xff;
  header[2] = (b.data.length >> 8) & 0xff;
  header[3] = b.data.length & 0xff;
  out.push(header, b.data);
}
out.push(buf.subarray(audioStart));

const result = Buffer.concat(out);
writeFileSync(dst, result);
console.log(`delta=${delta}, size ${buf.length} -> ${result.length}, identical size: ${buf.length === result.length}`);
