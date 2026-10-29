// 仅重现历史 v1.1.1 的原件封装；当前 Mac 版本请使用 package_macos.sh。
// 从已验证成品封装公开附件，不重新编译或修改可执行程序。
import { createHash } from 'node:crypto';
import { createWriteStream } from 'node:fs';
import { cp, mkdir, mkdtemp, readFile, readdir, writeFile, lstat } from 'node:fs/promises';
import { createRequire } from 'node:module';
import { tmpdir } from 'node:os';
import path from 'node:path';
import { fileURLToPath } from 'node:url';
import { execFileSync } from 'node:child_process';

const root = path.resolve(path.dirname(fileURLToPath(import.meta.url)), '..');
const require = createRequire(path.join(root, 'Windows/package.json'));
const archiver = require('archiver');
const destination = process.argv[2] ? path.resolve(process.argv[2]) : path.join(root, 'release-artifacts');
const windowsName = 'MathInterviewVocab-Windows-x64-v1.1.1.zip';
const macName = 'MathInterviewVocab-macOS-universal-v1.1.zip';
if (process.platform !== 'darwin') throw new Error('该封装步骤需要 macOS 的 ditto、codesign 和 lipo。');
await mkdir(destination, { recursive: true });
for (const name of [windowsName, macName, 'SHA256SUMS.txt']) {
    try { await lstat(path.join(destination, name)); }
    catch (error) { if (error.code === 'ENOENT') continue; throw error; }
    throw new Error(`拒绝覆盖现有发布附件：${name}`);
}

const digest = async file => createHash('sha256').update(await readFile(file)).digest('hex');
const workspace = await mkdtemp(path.join(tmpdir(), 'math-vocab-public-package-'));
const windowsInput = path.join(root, 'dist/数学面试词汇-Windows-x64.zip');
if (await digest(windowsInput) !== '84103c2f766f094e4642abd8ce59cc146911d0ea1501eb24676527a93a1a4708') {
    throw new Error('Windows输入包不是已验证的1.1.1成品。');
}
const windowsStage = path.join(workspace, 'windows');
await mkdir(windowsStage);
execFileSync('/usr/bin/ditto', ['-x', '-k', windowsInput, windowsStage]);
const windowsBundle = path.join(windowsStage, '数学面试词汇');
const windowsProgram = path.join(windowsBundle, '数学面试词汇.exe');
const executableHash = await digest(windowsProgram);
if (executableHash !== 'e38346ebcc036a3b90b14f235f99c0ec83da2d14514b4fca4961acc933913f5b') {
    throw new Error('Windows EXE 与已验证成品不符。');
}
const asarHash = await digest(path.join(windowsBundle, 'resources/app.asar'));
if (asarHash !== '034ecfb05c81855ba45e34e1903374d3426dbff88f6580aea2c64efcbf9842d2') {
    throw new Error('Windows应用资源与已验证成品不符。');
}
await cp(path.join(root, 'LICENSE'), path.join(windowsBundle, 'PROJECT-LICENSE.txt'));
await cp(path.join(root, 'Windows/PORTABLE-README.txt'), path.join(windowsBundle, '使用说明.txt'));

const macInput = path.join(root, 'dist/数学面试词汇.app');
const macStage = path.join(workspace, 'macos');
await mkdir(macStage);
const macBundle = path.join(macStage, '数学面试词汇.app');
execFileSync('/usr/bin/codesign', ['--verify', '--deep', '--strict', macInput]);
execFileSync('/usr/bin/ditto', ['--norsrc', macInput, macBundle]);
await cp(path.join(root, 'LICENSE'), path.join(macStage, 'PROJECT-LICENSE.txt'));
await cp(path.join(root, 'docs/INSTALL-macOS.txt'), path.join(macStage, '使用说明.txt'));
const macProgram = path.join(macBundle, 'Contents/MacOS/MathInterviewVocab');
const macHash = await digest(macProgram);
if (macHash !== '27e1ce2ea874ceaffed728091f7a755b4fb84c8845109bb09e04d0eeea74bc00') {
    throw new Error('Mac可执行文件与已验证成品不符。');
}
execFileSync('/usr/bin/codesign', ['--verify', '--deep', '--strict', macBundle]);
const architectures = execFileSync('/usr/bin/lipo', ['-archs', macProgram], { encoding: 'utf8' }).trim();
if (!architectures.includes('arm64') || !architectures.includes('x86_64')) throw new Error('Mac应用不是Universal。');

async function appendDirectory(archive, directory, prefix = '') {
    for (const entry of (await readdir(directory, { withFileTypes: true })).sort((a, b) => a.name.localeCompare(b.name))) {
        if (entry.name.startsWith('.') || entry.name === '__MACOSX' || entry.name.endsWith('.baiduyun.uploading.cfg')) continue;
        const source = path.join(directory, entry.name);
        const name = prefix ? `${prefix}/${entry.name}` : entry.name;
        if (entry.isDirectory()) await appendDirectory(archive, source, name);
        else if (entry.isSymbolicLink()) throw new Error(`未预期的符号链接：${name}`);
        else if (entry.isFile()) archive.file(source, { name, mode: (await lstat(source)).mode & 0o777 });
    }
}
async function writeArchive(directory, name) {
    const output = createWriteStream(path.join(destination, name), { flags: 'wx' });
    const archive = archiver('zip', { zlib: { level: 7 } });
    const completed = new Promise((resolve, reject) => {
        output.on('close', resolve); output.on('error', reject); archive.on('error', reject);
        archive.on('warning', reject);
    });
    archive.pipe(output);
    await appendDirectory(archive, directory);
    await archive.finalize();
    await completed;
    execFileSync('/usr/bin/unzip', ['-tq', path.join(destination, name)]);
    return { name, sha256: await digest(path.join(destination, name)) };
}
const entries = [await writeArchive(windowsStage, windowsName), await writeArchive(macStage, macName)];
await writeFile(path.join(destination, 'SHA256SUMS.txt'), entries.map(entry => `${entry.sha256}  ${entry.name}`).join('\n') + '\n', { flag: 'wx' });
console.log(JSON.stringify({ artifacts: entries, preserved: { windowsEXE: executableHash, windowsASAR: asarHash, macExecutable: macHash, architectures } }, null, 2));
