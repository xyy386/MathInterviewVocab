import { execFileSync } from 'node:child_process';
import { mkdir, readFile, rm, stat, writeFile } from 'node:fs/promises';
import { createHash, randomUUID } from 'node:crypto';
import path from 'node:path';
import { desktopIndex, macAppcast, verifyMacIndexSignature } from './prepare_desktop_release.mjs';

// 只操作显式的新验收目录，不运行或修改用户安装的应用。
const [source, output, tools, sourceArtifact, portText = '18769'] = process.argv.slice(2);
if (!source || !output || !tools || !sourceArtifact || !path.isAbsolute(output)
    || !output.includes('/Documents/codextest/') || !/^\d{4,5}$/.test(portText)
    || Number(portText) > 65535 || Number(portText) < 1024) {
    throw new Error('Usage: node Scripts/prepare_macos_update_fixture.mjs DEBUG_APP NEW_CODEXTEST_DIRECTORY SPARKLE_BIN FULL_SPARKLE_FRAMEWORK [PORT]');
}
const fixtureRoot = path.resolve(output);
const origin = `http://127.0.0.1:${portText}`;
const account = 'com.xingyiyao.MathInterviewVocab.sparkle';
const accelerated = process.env.MATH_VOCAB_ACCELERATED_FIXTURE === '1';
const version = accelerated ? '1.5.0' : '1.4.0';
const build = accelerated ? '7' : '6';
const token = randomUUID();
const bundleID = 'com.xingyiyao.MathInterviewVocab.UpdaterFixture' + (accelerated ? `.${token}` : '');
const appName = accelerated ? '数学词汇更新验收150.app' : '数学词汇更新验收.app';
await mkdir(fixtureRoot, { recursive: false });
const dataDirectory = path.join(fixtureRoot, 'data');
await mkdir(dataDirectory);
const progress = {
    attempts: [], favoriteIDs: [1], correctStreak: {}, wrongIDs: [2], masteredIDs: [],
    quizPreferences: { direction: 'mixed', scope: 'important', questionLength: 'twenty', selectedSubjectID: 'analysis', includeMastered: true },
};
await writeFile(path.join(dataDirectory, 'progress.json'), JSON.stringify(progress, null, 2) + '\n', { flag: 'wx' });
await writeFile(path.join(fixtureRoot, 'expected-progress.json'), JSON.stringify(progress, null, 2) + '\n', { flag: 'wx' });

for (const [name, fixtureVersion, fixtureBuild] of [['old', accelerated ? '1.4.9' : '1.3.9', accelerated ? '6' : '5'], ['new', version, build]]) {
    const directory = path.join(fixtureRoot, name);
    await mkdir(directory);
    const app = path.join(directory, appName);
    execFileSync('/usr/bin/ditto', ['--norsrc', path.resolve(source), app]);
    // 与正式打包一致：恢复 SPM 自动嵌入时被删掉的封存头文件，不重签 helper。
    execFileSync('/usr/bin/codesign', ['--verify', '--deep', '--strict', sourceArtifact], { stdio: 'pipe' });
    execFileSync('/usr/bin/ditto', ['--norsrc', sourceArtifact, path.join(app, 'Contents/Frameworks/Sparkle.framework')]);
    await rm(path.join(app, 'Contents/PlugIns/MathInterviewVocabTests.xctest'), { recursive: true, force: true });
    const plist = path.join(app, 'Contents/Info.plist');
    const information = JSON.parse(execFileSync('/usr/bin/plutil', ['-convert', 'json', '-o', '-', plist], { encoding: 'utf8' }));
    const commands = [
        `Set :CFBundleIdentifier ${bundleID}`,
        `Set :CFBundleDisplayName ${appName.slice(0, -4)}`,
        `Set :CFBundleShortVersionString ${fixtureVersion}`, `Set :CFBundleVersion ${fixtureBuild}`,
        ...(accelerated ? [`Add :MathVocabFixtureToken string ${token}`, `Add :MathVocabFixtureOrigin string ${origin}`] : [`Set :SUFeedURL ${origin}/appcast.xml`]),
        `Add :MathVocabFixtureDataDirectory string ${dataDirectory}`,
        ...(information.NSAppTransportSecurity ? [] : ['Add :NSAppTransportSecurity dict']),
        `${information.NSAppTransportSecurity?.NSAllowsLocalNetworking === undefined ? 'Add' : 'Set'} :NSAppTransportSecurity:NSAllowsLocalNetworking${information.NSAppTransportSecurity?.NSAllowsLocalNetworking === undefined ? ' bool' : ''} true`,
    ];
    for (const command of commands) execFileSync('/usr/libexec/PlistBuddy', ['-c', command, plist]);
    execFileSync('/usr/bin/codesign', ['--force', '--sign', '-', '--timestamp=none', app], { stdio: 'pipe' });
    execFileSync('/usr/bin/codesign', ['--verify', '--deep', '--strict', app], { stdio: 'pipe' });
}
const serveDirectory = path.join(fixtureRoot, 'server');
const archiveDirectory = path.join(serveDirectory, `releases/download/v${version}`);
await mkdir(archiveDirectory, { recursive: true });
const archive = path.join(archiveDirectory, `MathInterviewVocab-macOS-universal-v${version}.zip`);
execFileSync('/usr/bin/ditto', ['-c', '-k', '--norsrc', '--keepParent', path.join(fixtureRoot, 'new', appName), archive]);
const signTool = path.join(path.resolve(tools), 'sign_update');
const signature = execFileSync(signTool, ['--account', account, '-p', archive], { encoding: 'utf8' }).trim();
let xml = macAppcast({ version, build, bytes: (await stat(archive)).size, signature });
if (!accelerated) xml = xml.replace('https://github.com/xyy386/MathInterviewVocab/releases/download', `${origin}/releases/download`);
const feed = path.join(accelerated ? archiveDirectory : serveDirectory, 'appcast.xml');
await writeFile(feed, xml, { flag: 'wx' });
execFileSync(signTool, ['--account', account, feed], { stdio: 'pipe' });
execFileSync(signTool, ['--account', account, '--verify', feed], { stdio: 'pipe' });
if (accelerated) {
    const negative = path.join(serveDirectory, 'negative'); await mkdir(negative);
    const indexDirectory = path.join(serveDirectory, 'updates/macos'); await mkdir(indexDirectory, { recursive: true });
    const cache = path.join(dataDirectory, 'UpdatesCache'); await mkdir(cache);
    await writeFile(path.join(cache, 'preferences.json'), JSON.stringify({ routeMode: 'direct', knownVersion: '1.4.9' }) + '\n');
    const archiveData = await readFile(archive);
    for (const [directory, feedData] of [[indexDirectory, await readFile(feed)], [negative,
        Buffer.from((await readFile(feed, 'utf8')).replace('<title>数学面试词汇</title>', '<title>篡改的更新源</title>'))]]) {
        const data = desktopIndex({ platform: 'macos-universal', version, build, notes: '独立原生更新验收，不公开测试包。', assets: [
            { name: path.basename(archive), bytes: archiveData.length, sha256: createHash('sha256').update(archiveData).digest('hex') },
            { name: 'appcast.xml', bytes: feedData.length, sha256: createHash('sha256').update(feedData).digest('hex') },
        ] });
        const output = path.join(directory, 'latest.json'); await writeFile(output, data, { flag: 'wx' });
        const signed = execFileSync(signTool, ['--account', account, '-p', output], { encoding: 'utf8' }).trim();
        if (!verifyMacIndexSignature(data, signed)) throw new Error('Native index signature mismatch.');
        await writeFile(output + '.sig', signed + '\n', { flag: 'wx' });
        if (directory === negative) await writeFile(path.join(directory, 'appcast.xml'), feedData);
    }
    await writeFile(path.join(serveDirectory, 'mode.json'), JSON.stringify({ badFeed: false, chunkDelayMs: 30 }) + '\n');
    await writeFile(path.join(fixtureRoot, 'fixture.json'), JSON.stringify({ bundleID, version, build, origin,
        oldApp: path.join(fixtureRoot, 'old', appName), newApp: path.join(fixtureRoot, 'new', appName), dataDirectory,
        archive: path.basename(archive), serverDirectory: serveDirectory }, null, 2) + '\n');
}
console.log(`Fixture ready: ${fixtureRoot}\nServe only ${serveDirectory} on 127.0.0.1:${portText}; launch only old/${appName}.`);
