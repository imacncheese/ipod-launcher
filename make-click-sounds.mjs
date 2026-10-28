import { writeFileSync, mkdirSync } from 'node:fs';

// Short mechanical clicks, synthesised so no sample assets are needed.
function writeWav(path, samples) {
  const data = Buffer.alloc(samples.length * 2);
  for (let i = 0; i < samples.length; i++) {
    const v = Math.max(-1, Math.min(1, samples[i]));
    data.writeInt16LE(Math.round(v * 32767), i * 2);
  }
  const hdr = Buffer.alloc(44);
  hdr.write('RIFF', 0);
  hdr.writeUInt32LE(36 + data.length, 4);
  hdr.write('WAVE', 8);
  hdr.write('fmt ', 12);
  hdr.writeUInt32LE(16, 16);
  hdr.writeUInt16LE(1, 20);
  hdr.writeUInt16LE(1, 22);
  hdr.writeUInt32LE(44100, 24);
  hdr.writeUInt32LE(88200, 28);
  hdr.writeUInt16LE(2, 32);
  hdr.writeUInt16LE(16, 34);
  hdr.write('data', 36);
  hdr.writeUInt32LE(data.length, 40);
  writeFileSync(path, Buffer.concat([hdr, data]));
  console.log(`${path} ${(44 + data.length)} bytes`);
}

const RATE = 44100;

function noise(ms, decayMs, amp) {
  const n = Math.floor((RATE * ms) / 1000);
  const out = new Float32Array(n);
  for (let i = 0; i < n; i++) {
    const t = i / RATE;
    const env = Math.exp(-t / (decayMs / 1000));
    out[i] = (Math.random() * 2 - 1) * env * amp;
  }
  return out;
}

function tone(ms, freq, decayMs, amp) {
  const n = Math.floor((RATE * ms) / 1000);
  const out = new Float32Array(n);
  for (let i = 0; i < n; i++) {
    const t = i / RATE;
    const env = Math.exp(-t / (decayMs / 1000));
    out[i] = Math.sin(2 * Math.PI * freq * t) * env * amp;
  }
  return out;
}

function mix(a, b, gainB) {
  const n = Math.max(a.length, b.length);
  const out = new Float32Array(n);
  for (let i = 0; i < n; i++) {
    const va = i < a.length ? a[i] : 0;
    const vb = i < b.length ? b[i] : 0;
    out[i] = va + vb * gainB;
  }
  return out;
}

// A detent: a very short, dry tick with a high click and a tiny body.
const tick = mix(noise(6, 0.7, 0.55), tone(9, 3200, 1.6, 0.55), 1.0);
// A button press: lower, slightly longer, a bit more body.
const press = mix(noise(14, 1.6, 0.5), tone(22, 1500, 4.0, 0.6), 1.0);

mkdirSync('F:/dsh/ipod-launcher/app/res/raw', { recursive: true });
writeWav('F:/dsh/ipod-launcher/app/res/raw/wheel_tick.wav', tick);
writeWav('F:/dsh/ipod-launcher/app/res/raw/wheel_press.wav', press);
