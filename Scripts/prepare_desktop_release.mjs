import { createHash } from 'node:crypto';
import { createReadStream } from 'node:fs';
import { mkdir, readFile, stat, writeFile } from 'node:fs/promises';
import { execFileSync } from 'node:child_process';
import path from 'node:path';
import { fileURLToPath, pathToFileURL } from 'node:url';

const projectRoot = path.resolve(path.dirname(fileURLToPath(import.meta.url)), '..');
const account = 'com.xingyiyao.MathInterviewVocab.sparkle';
const publicKey = 'IBdRtdCy/pTzCtxPyODNo0saDZ5zbprza4wRUJ5O1DU=';

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
