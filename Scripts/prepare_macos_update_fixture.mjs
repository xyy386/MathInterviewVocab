import { execFileSync } from 'node:child_process';
import { mkdir, rm, stat, writeFile } from 'node:fs/promises';
import path from 'node:path';
import { macAppcast } from './prepare_desktop_release.mjs';

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
await mkdir(fixtureRoot, { recursive: false });
const dataDirectory = path.join(fixtureRoot, 'data');
await mkdir(dataDirectory);
const progress = {
    attempts: [], favoriteIDs: [1], correctStreak: {}, wrongIDs: [2], masteredIDs: [],
    quizPreferences: { direction: 'mixed', scope: 'important', questionLength: 'twenty', selectedSubjectID: 'analysis', includeMastered: true },
};
await writeFile(path.join(dataDirectory, 'progress.json'), JSON.stringify(progress, null, 2) + '\n', { flag: 'wx' });
await writeFile(path.join(fixtureRoot, 'expected-progress.json'), JSON.stringify(progress, null, 2) + '\n', { flag: 'wx' });

for (const [name, version, build] of [['old', '1.3.9', '5'], ['new', '1.4.0', '6']]) {
    const directory = path.join(fixtureRoot, name);
    await mkdir(directory);
    const app = path.join(directory, '数学词汇更新验收.app');
    execFileSync('/usr/bin/ditto', ['--norsrc', path.resolve(source), app]);
    // 与正式打包一致：恢复 SPM 自动嵌入时被删掉的封存头文件，不重签 helper。
    execFileSync('/usr/bin/codesign', ['--verify', '--deep', '--strict', sourceArtifact], { stdio: 'pipe' });
    execFileSync('/usr/bin/ditto', ['--norsrc', sourceArtifact, path.join(app, 'Contents/Frameworks/Sparkle.framework')]);
    await rm(path.join(app, 'Contents/PlugIns/MathInterviewVocabTests.xctest'), { recursive: true, force: true });
    const plist = path.join(app, 'Contents/Info.plist');
    const commands = [
        'Set :CFBundleIdentifier com.xingyiyao.MathInterviewVocab.UpdaterFixture',
        'Set :CFBundleDisplayName 数学词汇更新验收',
        `Set :CFBundleShortVersionString ${version}`, `Set :CFBundleVersion ${build}`,
        `Set :SUFeedURL ${origin}/appcast.xml`,
        `Add :MathVocabFixtureDataDirectory string ${dataDirectory}`,
        'Add :NSAppTransportSecurity dict',
        'Add :NSAppTransportSecurity:NSAllowsLocalNetworking bool true',
    ];
    for (const command of commands) execFileSync('/usr/libexec/PlistBuddy', ['-c', command, plist]);
    execFileSync('/usr/bin/codesign', ['--force', '--sign', '-', '--timestamp=none', app], { stdio: 'pipe' });
    execFileSync('/usr/bin/codesign', ['--verify', '--deep', '--strict', app], { stdio: 'pipe' });
}
const serveDirectory = path.join(fixtureRoot, 'server');
const archiveDirectory = path.join(serveDirectory, 'releases/download/v1.4.0');
await mkdir(archiveDirectory, { recursive: true });
const archive = path.join(archiveDirectory, 'MathInterviewVocab-macOS-universal-v1.4.0.zip');
execFileSync('/usr/bin/ditto', ['-c', '-k', '--norsrc', '--keepParent', path.join(fixtureRoot, 'new/数学词汇更新验收.app'), archive]);
const signTool = path.join(path.resolve(tools), 'sign_update');
const signature = execFileSync(signTool, ['--account', account, '-p', archive], { encoding: 'utf8' }).trim();
const xml = macAppcast({ version: '1.4.0', build: '6', bytes: (await stat(archive)).size, signature })
    .replace('https://github.com/xyy386/MathInterviewVocab/releases/download', `${origin}/releases/download`);
const feed = path.join(serveDirectory, 'appcast.xml');
await writeFile(feed, xml, { flag: 'wx' });
execFileSync(signTool, ['--account', account, feed], { stdio: 'pipe' });
execFileSync(signTool, ['--account', account, '--verify', feed], { stdio: 'pipe' });
console.log(`Fixture ready: ${fixtureRoot}\nServe only ${serveDirectory} on 127.0.0.1:${portText}; launch only old/数学词汇更新验收.app.`);
