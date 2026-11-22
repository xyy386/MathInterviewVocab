import { packager } from '@electron/packager';
import archiver from 'archiver';
import { createWriteStream } from 'node:fs';
import { readFile, writeFile, mkdir, rename, access } from 'node:fs/promises';
import path from 'node:path';
import { fileURLToPath } from 'node:url';
import { createHash } from 'node:crypto';
import { build, Platform, Arch } from 'electron-builder';

const root = path.resolve(path.dirname(fileURLToPath(import.meta.url)), '..');
const stage = path.join(root, '.out/app');
const metadata = JSON.parse(await readFile(path.join(root, 'package.json'), 'utf8'));
if (process.argv.length > 3) throw new Error('Usage: node scripts/package-windows.mjs [OUTPUT_DIRECTORY]');
const destination = process.argv[2] ? path.resolve(process.argv[2]) : path.resolve(root, '../dist');
const zipFile = path.join(destination, `MathInterviewVocab-Windows-x64-v${metadata.version}.zip`);
const installerFile = path.join(destination, `MathInterviewVocab-Windows-x64-Setup-v${metadata.version}.exe`);
try { await access(zipFile); throw new Error(`拒绝覆盖已有成品：${path.basename(zipFile)}`); }
catch (error) { if (error.code !== 'ENOENT') throw error; }
try { await access(installerFile); throw new Error(`拒绝覆盖已有成品：${path.basename(installerFile)}`); }
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
const mathManifest = JSON.parse(await readFile(path.join(stage, 'resources/math/manifest.json'), 'utf8'));
if (mathManifest.entries.length !== 63 || mathManifest.engine !== 'MathJax 4.1.3') throw new Error('公式资源不完整。');
for (const formula of mathManifest.entries) for (const type of ['svg', 'pdf']) {
    if (!/^[a-f0-9]{64}$/.test(formula.id) || formula[type] !== `${formula.id}.${type}`) throw new Error('无效公式路径。');
    const data = await readFile(path.join(stage, 'resources/math', formula[type]));
    if (createHash('sha256').update(data).digest('hex') !== formula[`${type}SHA256`]) throw new Error(`公式校验失败：${formula.id}`);
}
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
    name: '数学面试词汇', executableName: '数学面试词汇', appVersion: metadata.version, buildVersion: `${metadata.version}.${metadata.desktopBuild}`,
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
// 使用稳定版 NSIS；安装目录与学习记录目录分离，不申请提权或清空用户数据。
await build({ projectDir: stage, prepackaged: bundle, targets: Platform.WINDOWS.createTarget('nsis', Arch.x64),
    publish: 'never', config: {
        appId: 'com.xingyiyao.MathInterviewVocab', productName: metadata.productName,
        executableName: '数学面试词汇', buildVersion: `${metadata.version}.${metadata.desktopBuild}`,
        directories: { output: destination, buildResources: path.join(root, 'scripts') },
        electronVersion: metadata.devDependencies.electron,
        win: { target: ['nsis'], icon: path.join(root, '.out/icon.ico'), signAndEditExecutable: false,
            artifactName: `MathInterviewVocab-Windows-x64-Setup-v${metadata.version}.exe` },
        nsis: { oneClick: false, perMachine: false, allowElevation: false, packElevateHelper: false,
            allowToChangeInstallationDirectory: false, deleteAppDataOnUninstall: false,
            shortcutName: '数学面试词汇', createDesktopShortcut: true, createStartMenuShortcut: true,
            runAfterFinish: true, installerLanguages: ['zh_CN', 'en_US'],
            include: path.join(root, 'scripts/installer.nsh'), differentialPackage: false,
            license: path.join(root, '../LICENSE') },
        forceCodeSigning: false,
    } });
const archive = archiver('zip', { zlib: { level: 7 } });
const output = createWriteStream(zipFile + '.tmp');
const complete = new Promise((resolve, reject) => { output.on('close', resolve); output.on('error', reject); archive.on('error', reject); });
archive.pipe(output); archive.directory(bundle, '数学面试词汇'); await archive.finalize(); await complete;
await rename(zipFile + '.tmp', zipFile);
const zip = await readFile(zipFile);
const installer = await readFile(installerFile);
const report = { version: metadata.version, target: 'win32-x64', machine: 'AMD64 (0x8664)',
    terms: terms.length, important: terms.filter(term => term.important).length,
    audioFiles: entries.length, audioSource: manifest.source,
    zip: path.basename(zipFile), bytes: zip.length, sha256: createHash('sha256').update(zip).digest('hex'),
    installer: { file: path.basename(installerFile), bytes: installer.length, sha256: createHash('sha256').update(installer).digest('hex') },
    builtAt: new Date().toISOString(), windowsNativeExecutionTested: false, parallelsUsed: false };
console.log(JSON.stringify(report, null, 2));
