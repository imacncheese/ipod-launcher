import { mkdirSync, createWriteStream, statSync } from 'node:fs';
import { Readable } from 'node:stream';
import { pipeline } from 'node:stream/promises';

const dest = 'F:/dsh/ipod-launcher/toolchain/downloads';
mkdirSync(dest, { recursive: true });

const jobs = [
  {
    name: 'jdk17.zip',
    urls: [
      'https://api.adoptium.net/v3/binary/latest/17/ga/windows/x64/jdk/hotspot/normal/eclipse',
    ],
  },
  {
    name: 'build-tools.zip',
    urls: [
      'https://dl.google.com/android/repository/build-tools_r35_windows.zip',
      'https://dl.google.com/android/repository/build-tools_r35-windows.zip',
      'https://dl.google.com/android/repository/build-tools_r34-windows.zip',
    ],
  },
  {
    name: 'platform.zip',
    urls: [
      'https://dl.google.com/android/repository/platform-35_r02.zip',
      'https://dl.google.com/android/repository/platform-35_r01.zip',
      'https://dl.google.com/android/repository/platform-34_r02.zip',
    ],
  },
];

for (const job of jobs) {
  let ok = false;
  for (const url of job.urls) {
    try {
      const res = await fetch(url, { redirect: 'follow' });
      if (!res.ok) {
        console.log(`skip ${url} -> HTTP ${res.status}`);
        continue;
      }
      const out = `${dest}/${job.name}`;
      await pipeline(Readable.fromWeb(res.body), createWriteStream(out));
      const size = statSync(out).size;
      console.log(`OK ${job.name} ${size} bytes  <- ${url}`);
      ok = true;
      break;
    } catch (e) {
      console.log(`fail ${url}: ${e.message}`);
    }
  }
  if (!ok) {
    console.log(`FAILED ${job.name}`);
    process.exitCode = 1;
  }
}
console.log('downloads done');
