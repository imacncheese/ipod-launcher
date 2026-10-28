import { writeFileSync, mkdirSync } from 'node:fs';

function wav(path, freq, seconds) {
  const rate = 44100;
  const n = Math.floor(rate * seconds);
  const data = Buffer.alloc(n * 2);
  for (let i = 0; i < n; i++) {
    // fade in/out so it does not click
    const env = Math.min(1, i / 2000, (n - i) / 2000);
    const v = Math.round(Math.sin((2 * Math.PI * freq * i) / rate) * 9000 * env);
    data.writeInt16LE(v, i * 2);
  }
  const hdr = Buffer.alloc(44);
  hdr.write('RIFF', 0);
  hdr.writeUInt32LE(36 + data.length, 4);
  hdr.write('WAVE', 8);
  hdr.write('fmt ', 12);
  hdr.writeUInt32LE(16, 16);
  hdr.writeUInt16LE(1, 20);
  hdr.writeUInt16LE(1, 22);
  hdr.writeUInt32LE(rate, 24);
  hdr.writeUInt32LE(rate * 2, 28);
  hdr.writeUInt16LE(2, 32);
  hdr.writeUInt16LE(16, 34);
  hdr.write('data', 36);
  hdr.writeUInt32LE(data.length, 40);
  writeFileSync(path, Buffer.concat([hdr, data]));
  console.log(`${path}  ${((36 + data.length) / 1024).toFixed(0)} KB`);
}

mkdirSync('F:/dsh/ipod-launcher/testmedia', { recursive: true });
wav('F:/dsh/ipod-launcher/testmedia/A440 Ocean Drive.wav', 440, 7);
wav('F:/dsh/ipod-launcher/testmedia/B523 Neon Skyline.wav', 523, 6);
wav('F:/dsh/ipod-launcher/testmedia/C659 Midnight Run.wav', 659, 5);
