import { packager } from '@electron/packager';
import archiver from 'archiver';
import { createWriteStream } from 'node:fs';
import { readFile, writeFile, mkdir, rename, access } from 'node:fs/promises';
import path from 'node:path';
import { fileURLToPath } from 'node:url';
import { createHash } from 'node:crypto';

const root = path.resolve(path.dirname(fileURLToPath(import.meta.url)), '..');
const stage = path.join(root, '.out/app');
const metadata = JSON.parse(await readFile(path.join(root, 'package.json'), 'utf8'));
if (process.argv.length > 3) throw new Error('Usage: node scripts/package-windows.mjs [OUTPUT_DIRECTORY]');
const destination = process.argv[2] ? path.resolve(process.argv[2]) : path.resolve(root, '../dist');
const zipFile = path.join(destination, `MathInterviewVocab-Windows-x64-v${metadata.version}.zip`);
try { await access(zipFile); throw new Error(`拒绝覆盖已有成品：${path.basename(zipFile)}`); }
catch (error) { if (error.code !== 'ENOENT') throw error; }
const manifest = JSON.parse(await readFile(path.join(stage, 'resources/audio-manifest.json'), 'utf8'));
const entries = Object.entries(manifest.entries);
if (entries.length < 648 || !manifest.source || !manifest.coverage?.complete || manifest.coverage.expectedEntries !== entries.length) {
    throw new Error('离线音频未完成，拒绝生成最终交付包。');
}
for (const [key, entry] of entries) {
    if (!/^[a-f0-9]{64}$/.test(key) || entry.file !== `${key}.mp3`) throw new Error(`无效音频路径：${key}`);
    const audio = await readFile(path.join(stage, 'resources/audio', entry.file));
    if (audio.length !== entry.bytes || createHash('sha256').update(audio).digest('hex') !== entry.sha256) {
        throw new Error(`音频校验失败：${entry.file}`);
    }
}
const terms = JSON.parse(await readFile(path.join(stage, 'resources/terms.json'), 'utf8'));
const { normalizeAudioText, sanitizeSelection } = await import('../.out/core-audit.mjs');
for (const term of terms) {
    const text = normalizeAudioText(sanitizeSelection(term.speechText) ?? '');
    for (const accent of ['en-US', 'en-GB']) {
        const key = createHash('sha256').update(`${accent}\n${text}`).digest('hex');
        if (!manifest.entries[key]) throw new Error(`缺失完整词组 ${term.id} ${accent}`);
    }
}

const paths = await packager({
    dir: stage, out: path.join(root, '.out/win'), platform: 'win32', arch: 'x64',
    name: '数学面试词汇', executableName: '数学面试词汇', appVersion: metadata.version, buildVersion: `${metadata.version}.5`,
    electronVersion: metadata.devDependencies.electron, overwrite: true, asar: true, prune: true,
    icon: path.join(root, '.out/icon.ico'),
    win32metadata: { CompanyName: 'MathInterviewVocab', FileDescription: '数学面试词汇 · 全离线学习版', ProductName: '数学面试词汇',
        InternalName: 'MathInterviewVocab', OriginalFilename: '数学面试词汇.exe', 'requested-execution-level': 'asInvoker' },
});
const bundle = paths[0];
const exe = await readFile(path.join(bundle, '数学面试词汇.exe'));
const peOffset = exe.readUInt32LE(0x3c);
if (exe.toString('ascii', peOffset, peOffset + 4) !== 'PE\0\0' || exe.readUInt16LE(peOffset + 4) !== 0x8664) throw new Error('EXE 不是 AMD64 / x64。');

await writeFile(path.join(bundle, '使用说明.txt'), await readFile(path.join(root, 'PORTABLE-README.txt')));
await writeFile(path.join(bundle, 'PROJECT-LICENSE.txt'), await readFile(path.join(root, '../LICENSE')));
await writeFile(path.join(bundle, '第三方许可.txt'), await readFile(path.join(root, 'THIRD-PARTY-NOTICES.txt')));
await writeFile(path.join(bundle, '语音来源.md'), await readFile(path.join(root, 'resources/AUDIO-SOURCES.md')));
await writeFile(path.join(bundle, 'KOKORO-APACHE-2.0.txt'), await readFile(path.join(root, 'resources/KOKORO-APACHE-2.0.txt')));
await mkdir(destination, { recursive: true });
const archive = archiver('zip', { zlib: { level: 7 } });
const output = createWriteStream(zipFile + '.tmp');
const complete = new Promise((resolve, reject) => { output.on('close', resolve); output.on('error', reject); archive.on('error', reject); });
archive.pipe(output); archive.directory(bundle, '数学面试词汇'); await archive.finalize(); await complete;
await rename(zipFile + '.tmp', zipFile);
const zip = await readFile(zipFile);
const report = { version: metadata.version, target: 'win32-x64', machine: 'AMD64 (0x8664)',
    terms: terms.length, important: terms.filter(term => term.important).length,
    audioFiles: entries.length, audioSource: manifest.source,
    zip: path.basename(zipFile), bytes: zip.length, sha256: createHash('sha256').update(zip).digest('hex'),
    builtAt: new Date().toISOString(), windowsNativeExecutionTested: false, parallelsUsed: false };
await mkdir(path.join(root, 'verification'), { recursive: true });
await writeFile(path.join(root, 'verification/release.json'), JSON.stringify(report, null, 2) + '\n');
console.log(JSON.stringify(report, null, 2));
