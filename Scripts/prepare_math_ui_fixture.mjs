import { execFileSync } from 'node:child_process';
import { randomUUID } from 'node:crypto';
import { mkdir, rm, writeFile } from 'node:fs/promises';
import path from 'node:path';

const [source, output, framework] = process.argv.slice(2);
if (!source || !framework || !output?.startsWith('/') || !output.includes('/Documents/codextest/')) throw new Error('Expected DEBUG_APP NEW_CODEXTEST_DIRECTORY FULL_SPARKLE_FRAMEWORK');
await mkdir(output, { recursive: false });
const data = path.join(output, 'data'); await mkdir(data);
const token = randomUUID(), bundleID = 'com.xingyiyao.MathInterviewVocab.UpdaterFixture.' + token.toLowerCase();
const app = path.join(output, '数学公式验收151.app');
execFileSync('/usr/bin/ditto', ['--norsrc', source, app]);
await rm(path.join(app, 'Contents/PlugIns/MathInterviewVocabTests.xctest'), { recursive: true, force: true });
execFileSync('/usr/bin/codesign', ['--verify', '--deep', '--strict', framework]);
execFileSync('/usr/bin/ditto', ['--norsrc', framework, path.join(app, 'Contents/Frameworks/Sparkle.framework')]);
const plist = path.join(app, 'Contents/Info.plist');
for (const command of [`Set :CFBundleIdentifier ${bundleID}`, 'Set :CFBundleDisplayName 数学公式验收151',
    `Add :MathVocabFixtureToken string ${token}`, `Add :MathVocabFixtureDataDirectory string ${data}`]) {
    execFileSync('/usr/libexec/PlistBuddy', ['-c', command, plist]);
}
execFileSync('/usr/bin/codesign', ['--force', '--sign', '-', '--timestamp=none', app]);
execFileSync('/usr/bin/codesign', ['--verify', '--deep', '--strict', app]);
await writeFile(path.join(output, 'fixture.json'), JSON.stringify({ app, data, bundleID }, null, 2) + '\n');
console.log(app);
