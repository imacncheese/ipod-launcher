import { createWriteStream, mkdirSync, statSync } from 'node:fs';
import { Readable } from 'node:stream';
import { pipeline } from 'node:stream/promises';

const dest = 'F:/dsh/toolchain/git';
mkdirSync(dest, { recursive: true });

const relRes = await fetch('https://api.github.com/repos/git-for-windows/git/releases/latest', {
  headers: { 'User-Agent': 'dsh-agent' },
});
if (!relRes.ok) throw new Error('release lookup failed: ' + relRes.status);
const rel = await relRes.json();
const asset = (rel.assets || []).find((a) => /^MinGit-.*-64-bit\.zip$/.test(a.name));
if (!asset) throw new Error('no MinGit asset in ' + rel.tag_name);

console.log(`downloading ${asset.name} from ${rel.tag_name}`);
const res = await fetch(asset.browser_download_url, { redirect: 'follow' });
if (!res.ok) throw new Error('download failed: ' + res.status);
const out = `${dest}/mingit.zip`;
await pipeline(Readable.fromWeb(res.body), createWriteStream(out));
console.log(`saved ${out} ${statSync(out).size} bytes`);
