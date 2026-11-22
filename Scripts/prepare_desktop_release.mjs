import { createHash, createPrivateKey, createPublicKey, sign, verify } from 'node:crypto';
import { createReadStream } from 'node:fs';
import { mkdir, readFile, stat, writeFile } from 'node:fs/promises';
import { execFileSync } from 'node:child_process';
import path from 'node:path';
import { fileURLToPath, pathToFileURL } from 'node:url';

const projectRoot = path.resolve(path.dirname(fileURLToPath(import.meta.url)), '..');
const account = 'com.xingyiyao.MathInterviewVocab.sparkle';
const publicKey = 'IBdRtdCy/pTzCtxPyODNo0saDZ5zbprza4wRUJ5O1DU=';

export function desktopIndex({ platform, version, build, notes, assets }) {
    if (!['macos-universal', 'windows-x64'].includes(platform) || !/^(0|[1-9]\d*)\.(0|[1-9]\d*)\.(0|[1-9]\d*)$/.test(version)
        || typeof notes !== 'string' || !Array.isArray(assets)) throw new Error('Invalid desktop update index.');
    const expected = platform === 'macos-universal'
        ? [`MathInterviewVocab-macOS-universal-v${version}.zip`, 'appcast.xml']
        : [`MathInterviewVocab-Windows-x64-Setup-v${version}.exe`, 'Windows-update.json', 'Windows-update.json.sig'];
    if (platform === 'macos-universal' ? !/^[1-9]\d*$/.test(build) : !new RegExp(`^${version.replaceAll('.', '\\.')}\\.[1-9]\\d*$`).test(build)) {
        throw new Error('Invalid desktop build.');
    }
    if (assets.length !== expected.length || new Set(assets.map(asset => asset.name)).size !== expected.length
        || assets.some(asset => !expected.includes(asset.name) || !Number.isSafeInteger(asset.bytes) || asset.bytes <= 0
            || !/^[a-f0-9]{64}$/.test(asset.sha256) || Object.keys(asset).sort().join(',') !== 'bytes,name,sha256')) {
        throw new Error('Invalid signed index assets.');
    }
    return Buffer.from(JSON.stringify({ schema: 1, platform, version, build, notes: notes.slice(0, 8000), assets }, null, 2) + '\n');
}

export function verifyMacIndexSignature(data, signature) {
    const key = createPublicKey({ format: 'der', type: 'spki', key: Buffer.concat([
        Buffer.from('302a300506032b6570032100', 'hex'), Buffer.from(publicKey, 'base64'),
    ]) });
    return verify(null, data, key, Buffer.from(signature.trim(), 'base64'));
}

export function macAppcast({ version, build, bytes, signature }) {
    if (!/^\d+\.\d+\.\d+$/.test(version) || !/^[1-9]\d*$/.test(build)
        || !Number.isSafeInteger(bytes) || bytes <= 0
        || !/^[A-Za-z0-9+/]{86}==$/.test(signature)) {
        throw new Error('Invalid signed macOS update metadata.');
    }
    const fileName = `MathInterviewVocab-macOS-universal-v${version}.zip`;
    const release = `https://github.com/xyy386/MathInterviewVocab/releases/tag/v${version}`;
    return `<?xml version="1.0" encoding="utf-8"?>
<rss version="2.0" xmlns:sparkle="http://www.andymatuschak.org/xml-namespaces/sparkle">
  <channel>
    <title>数学面试词汇</title>
    <link>https://github.com/xyy386/MathInterviewVocab</link>
    <description>macOS 正式版；仅在用户主动检查时使用。</description>
    <item>
      <title>数学面试词汇 ${version}</title>
      <link>${release}</link>
      <sparkle:version>${build}</sparkle:version>
      <sparkle:shortVersionString>${version}</sparkle:shortVersionString>
      <sparkle:minimumSystemVersion>14.0</sparkle:minimumSystemVersion>
      <description><![CDATA[<p>更新说明：<a href="${release}">${version} 正式发布页</a>。确认安装后自动退出、替换并重新打开；学习记录保留。</p>]]></description>
      <enclosure url="https://github.com/xyy386/MathInterviewVocab/releases/download/v${version}/${fileName}" length="${bytes}" type="application/octet-stream" sparkle:edSignature="${signature}" />
    </item>
  </channel>
</rss>
`;
}

async function digest(file) {
    const hash = createHash('sha256');
    for await (const chunk of createReadStream(file)) hash.update(chunk);
    return hash.digest('hex');
}

async function prepare(args) {
    if (args.length !== 4 || !process.env.WINDOWS_UPDATE_SIGNING_KEY) {
        throw new Error('Usage: WINDOWS_UPDATE_SIGNING_KEY=/private/key.pem node Scripts/prepare_desktop_release.mjs MACOS_DIRECTORY WINDOWS_DIRECTORY NEW_OUTPUT_DIRECTORY SPARKLE_BIN');
    }
    const [macDirectory, windowsDirectory, outputDirectory, sparkleBin] = args.map(value => path.resolve(value));
    const metadata = JSON.parse(await readFile(path.join(projectRoot, 'Windows/package.json'), 'utf8'));
    const version = metadata.version;
    const macZIP = path.join(macDirectory, `MathInterviewVocab-macOS-universal-v${version}.zip`);
    const windowsZIP = path.join(windowsDirectory, `MathInterviewVocab-Windows-x64-v${version}.zip`);
    const installer = path.join(windowsDirectory, `MathInterviewVocab-Windows-x64-Setup-v${version}.exe`);
    const plist = path.join(macDirectory, '数学面试词汇.app/Contents/Info.plist');
    const info = JSON.parse(execFileSync('/usr/bin/plutil', ['-convert', 'json', '-o', '-', plist], { encoding: 'utf8' }));
    if (info.CFBundleShortVersionString !== version || info.SUPublicEDKey !== publicKey
        || info.SURequireSignedFeed !== true || info.SUVerifyUpdateBeforeExtraction !== true) {
        throw new Error('macOS version or update trust configuration does not match the release.');
    }
    for (const file of [macZIP, windowsZIP, installer]) {
        if (!(await stat(file)).isFile()) throw new Error(`Not a release file: ${path.basename(file)}`);
    }

    // 输出目录必须是新的；不改已有版本、签名或校验文件。
    await mkdir(outputDirectory, { recursive: false });
    const signingTool = path.join(sparkleBin, 'sign_update');
    const signature = execFileSync(signingTool, ['--account', account, '-p', macZIP], { encoding: 'utf8' }).trim();
    execFileSync(signingTool, ['--account', account, '--verify', macZIP, signature], { stdio: 'pipe' });
    const feed = path.join(outputDirectory, 'appcast.xml');
    await writeFile(feed, macAppcast({ version, build: info.CFBundleVersion, bytes: (await stat(macZIP)).size, signature }), { flag: 'wx' });
    execFileSync(signingTool, ['--account', account, feed], { stdio: 'pipe' });
    execFileSync(signingTool, ['--account', account, '--verify', feed], { stdio: 'pipe' });
    execFileSync(process.execPath, [path.join(projectRoot, 'Windows/scripts/sign-update.mjs'),
        '--installer', installer, '--key', process.env.WINDOWS_UPDATE_SIGNING_KEY, '--output', outputDirectory], { stdio: 'pipe' });

    const assets = [macZIP, windowsZIP, installer, feed,
        path.join(outputDirectory, 'Windows-update.json'), path.join(outputDirectory, 'Windows-update.json.sig')];
    const notes = await readFile(path.join(projectRoot, `docs/RELEASE-NOTES-v${version}.md`), 'utf8');
    const windowsKey = createPrivateKey(await readFile(process.env.WINDOWS_UPDATE_SIGNING_KEY));
    const pinned = /-----BEGIN PUBLIC KEY-----[\s\S]+?-----END PUBLIC KEY-----/.exec(await readFile(path.join(projectRoot, 'Windows/src/main/update-signature.ts'), 'utf8'))?.[0];
    if (!pinned || createPublicKey(windowsKey).export({ type: 'spki', format: 'pem' }).toString().trim() !== pinned.trim()) throw new Error('Windows signing key mismatch.');
    for (const [platform, build, inputs, filename] of [
        ['macos-universal', info.CFBundleVersion, [macZIP, feed], 'macOS-update-index.json'],
        ['windows-x64', `${version}.${metadata.desktopBuild}`, [installer, assets[4], assets[5]], 'Windows-update-index.json'],
    ]) {
        const entries = [];
        for (const file of inputs) entries.push({ name: path.basename(file), bytes: (await stat(file)).size, sha256: await digest(file) });
        const data = desktopIndex({ platform, version, build, notes, assets: entries });
        const file = path.join(outputDirectory, filename);
        await writeFile(file, data, { flag: 'wx' });
        const signed = platform === 'macos-universal'
            ? execFileSync(signingTool, ['--account', account, '-p', file], { encoding: 'utf8' }).trim()
            : sign(null, data, windowsKey).toString('base64');
        if (platform === 'macos-universal' ? !verifyMacIndexSignature(data, signed) : !verify(null, data, createPublicKey(pinned), Buffer.from(signed, 'base64'))) {
            throw new Error('Index signature read-back failed.');
        }
        await writeFile(file + '.sig', signed + '\n', { flag: 'wx' });
        assets.push(file, file + '.sig');
    }
    const checksums = [];
    for (const file of assets) checksums.push(`${await digest(file)}  ${path.basename(file)}`);
    await writeFile(path.join(outputDirectory, 'SHA256SUMS.txt'), checksums.join('\n') + '\n', { flag: 'wx' });
    console.log(`Prepared signed desktop ${version} metadata. Private keys were not copied.`);
}

if (process.argv[1] && import.meta.url === pathToFileURL(path.resolve(process.argv[1])).href) {
    prepare(process.argv.slice(2)).catch(error => {
        console.error(error.message);
        process.exitCode = 1;
    });
}
