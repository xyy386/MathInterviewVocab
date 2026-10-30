import test from 'node:test';
import assert from 'node:assert/strict';
import { createHash } from 'node:crypto';
import { mkdtemp, readdir, readFile, rm, writeFile } from 'node:fs/promises';
import { tmpdir } from 'node:os';
import path from 'node:path';
import { checksumFor, githubTransport, latestReleaseURL, markInternetDownload, maximumArchiveBytes, newerVersion, parseRelease,
    readBounded, redirectedURL, SoftwareUpdater, trustedURL, versionParts,
    type UpdateResponse, type UpdateTransport } from '../src/main/updates';
import type { UpdateState } from '../src/shared/update';

const base = 'https://github.com/xyy386/MathInterviewVocab';
const version = '1.3.0';
const archiveName = `MathInterviewVocab-Windows-x64-v${version}.zip`;
const archive = Buffer.from('PK\x03\x04 deterministic update fixture');
const digest = createHash('sha256').update(archive).digest('hex');
const checksums = Buffer.from(`${digest}  ${archiveName}\n`);
function metadata() {
    return { tag_name: `v${version}`, html_url: `${base}/releases/tag/v${version}`, draft: false, prerelease: false, body: '<script>Not HTML</script>\nRelease notes',
        assets: [{ name: archiveName, state: 'uploaded', digest: `sha256:${digest}`, size: archive.length, browser_download_url: `${base}/releases/download/v${version}/${archiveName}` },
            { name: 'SHA256SUMS.txt', state: 'uploaded', digest: `sha256:${createHash('sha256').update(checksums).digest('hex')}`, size: checksums.length, browser_download_url: `${base}/releases/download/v${version}/SHA256SUMS.txt` }] };
}
function response(value: Buffer, declared = value.length): UpdateResponse {
    return { contentLength: declared, body: (async function* () { yield value.subarray(0, 5); yield value.subarray(5); })() };
}
const fixtureTransport: UpdateTransport = async url => {
    if (url === latestReleaseURL) return response(Buffer.from(JSON.stringify(metadata())));
    if (url.endsWith('/SHA256SUMS.txt')) return response(checksums);
    if (url.endsWith(`/${archiveName}`)) return response(archive);
    throw new Error('Unexpected fixture URL');
};
async function fixture(run: (directory: string) => Promise<void>) {
    const directory = await mkdtemp(path.join(tmpdir(), 'vocab-update-test-'));
    try { await run(directory); }
    finally { await rm(directory, { recursive: true, force: true }); }
}
async function files(directory: string): Promise<string[]> {
    const result: string[] = [];
    for (const entry of await readdir(directory, { withFileTypes: true })) {
        const filename = path.join(directory, entry.name);
        if (entry.isDirectory()) result.push(...await files(filename)); else result.push(filename);
    }
    return result;
}

test('stable versions compare numerically instead of lexicographically', () => {
    assert.equal(newerVersion('1.10.0', '1.2.0'), true);
    assert.equal(newerVersion('2.0.0', '1.99.99'), true);
    assert.equal(newerVersion('1.2.0', '1.2.0'), false);
    assert.equal(newerVersion('1.1.2', '1.2.0'), false);
    assert.deepEqual(versionParts('0.0.0'), [0, 0, 0]);
});
for (const input of ['v1.2.0', '1.2', '01.2.0', '1.2.0-beta', '1.2.0+4', '1.2.-1', '1.2.Infinity', '1.2.9007199254740992', '1.2.0\n']) {
    test(`reject invalid version ${JSON.stringify(input)}`, () => assert.throws(() => versionParts(input)));
}
for (const input of ['http://github.com/release', 'https://github.com.evil.test/release', 'https://api.github.com@evil.test/',
    'https://name@github.com/release', 'https://github.com:444/release', 'file:///tmp/update.zip', 'https://github.com/release#fragment']) {
    test(`reject untrusted update URL ${input}`, () => assert.throws(() => trustedURL(input)));
}
test('redirects allow only HTTPS exact GitHub hosts and at most five hops', () => {
    assert.equal(redirectedURL('https://release-assets.githubusercontent.com/test?sig=x', base, 4), 'https://release-assets.githubusercontent.com/test?sig=x');
    assert.throws(() => redirectedURL('https://release-assets.githubusercontent.com/test', base, 5));
    assert.throws(() => redirectedURL('//evil.test/file', base, 0));
    assert.throws(() => redirectedURL('http://github.com/file', base, 0));
});
test('release requires stable official tag and exactly one matching x64 ZIP and checksum attachment', () => {
    assert.equal(parseRelease(metadata()).archive.name, archiveName);
    const mutations = [
        (value: ReturnType<typeof metadata>) => { value.draft = true; },
        (value: ReturnType<typeof metadata>) => { value.prerelease = true; },
        (value: ReturnType<typeof metadata>) => { value.tag_name = 'v1.3.0-beta'; },
        (value: ReturnType<typeof metadata>) => { value.html_url = 'https://github.com/other/repo/releases/tag/v1.3.0'; },
        (value: ReturnType<typeof metadata>) => { value.assets.push(value.assets[0]); },
        (value: ReturnType<typeof metadata>) => { value.assets.pop(); },
        (value: ReturnType<typeof metadata>) => { value.assets[0].browser_download_url += '?extra=1'; },
        (value: ReturnType<typeof metadata>) => { value.assets[0].browser_download_url = value.assets[0].browser_download_url.replace('/v1.3.0/', '/v1.2.0/'); },
        (value: ReturnType<typeof metadata>) => { value.assets[0].size = maximumArchiveBytes + 1; },
        (value: ReturnType<typeof metadata>) => { value.assets[0].size = 0; },
        (value: ReturnType<typeof metadata>) => { value.assets[0].state = 'new'; },
        (value: ReturnType<typeof metadata>) => { value.assets[0].digest = 'sha256:invalid'; },
    ];
    for (const mutate of mutations) { const value = metadata(); mutate(value); assert.throws(() => parseRelease(value)); }
    const long = metadata(); long.body = 'a'.repeat(9000); assert.equal(parseRelease(long).notes.length, 8000);
});
test('checksums support CRLF and binary marker but reject duplicates, paths and malformed values', () => {
    assert.equal(checksumFor(`${digest.toUpperCase()} *${archiveName}\r\n`, archiveName), digest);
    for (const text of [`${digest}  ../${archiveName}\n`, `${digest}  ${archiveName}\n${digest}  ${archiveName}\n`,
        `${digest.slice(1)}  ${archiveName}\n`, `${digest}  another.zip\n`, `${digest}  dir/file.zip\n`, `${digest}  C:\\file.zip\n`]) {
        assert.throws(() => checksumFor(text, archiveName));
    }
});
test('bounded metadata reader enforces header, streamed byte and complete-size limits', async () => {
    const signal = new AbortController().signal;
    assert.equal((await readBounded(response(archive), archive.length, signal)).length, archive.length);
    await assert.rejects(readBounded(response(archive), 1, signal));
    await assert.rejects(readBounded({ body: response(archive).body }, 1, signal));
    await assert.rejects(readBounded(response(archive, archive.length + 1), 1000, signal, archive.length));
    await assert.rejects(readBounded({ body: response(archive).body }, 1000, signal, archive.length + 1));
    const controller = new AbortController(); controller.abort();
    await assert.rejects(readBounded(response(archive), 1000, controller.signal));
});
test('transport rejects untrusted URL before a socket can open', async () => {
    await assert.rejects(githubTransport('http://evil.test/archive.zip', new AbortController().signal));
});
test('updater downloads only on request and reveals verified ZIP only on an additional user action', async () => fixture(async directory => {
    const states: UpdateState[] = [];
    const revealed: string[] = [];
    const pages: string[] = [];
    const updater = new SoftwareUpdater('1.2.0', directory, state => states.push(state), file => revealed.push(file), async url => { pages.push(url); }, fixtureTransport);
    assert.equal(updater.state.status, 'idle');
    assert.equal((await files(directory)).length, 0);
    assert.equal((await updater.check()).status, 'available');
    assert.equal((await files(directory)).length, 0);
    await updater.showRelease(); assert.deepEqual(pages, [`${base}/releases/tag/v${version}`]);
    assert.equal((await updater.download()).status, 'ready');
    assert.equal(revealed.length, 0);
    const downloaded = (await files(directory))[0];
    assert.equal(path.basename(downloaded), archiveName);
    assert.deepEqual(await readFile(downloaded), archive);
    assert.ok(states.some(state => state.status === 'downloading'));
    assert.equal((await files(directory)).length, 1);
    updater.showDownload(); assert.deepEqual(revealed, [downloaded]);
    await updater.download(); assert.equal((await files(directory)).length, 1);
}));
test('older legacy release needs no matching modern assets and cannot trigger downgrade', async () => fixture(async directory => {
    const older = { ...metadata(), tag_name: 'v1.1.2', html_url: `${base}/releases/tag/v1.1.2`, assets: [] };
    const updater = new SoftwareUpdater('1.2.0', directory, () => {}, () => assert.fail('must not reveal'), async () => {}, async () => response(Buffer.from(JSON.stringify(older))));
    assert.equal((await updater.check()).status, 'current');
    assert.equal((await updater.download()).status, 'current');
    assert.equal((await files(directory)).length, 0);
}));
for (const failure of ['hash', 'too-large', 'incomplete', 'network'] as const) {
    test(`failed ${failure} download cannot reveal ZIP or retain a partial file`, async () => fixture(async directory => {
        const transport: UpdateTransport = async (url, signal) => {
            if (!url.endsWith(`/${archiveName}`)) return fixtureTransport(url, signal);
            if (failure === 'network') throw new Error('Offline fixture');
            if (failure === 'hash') return response(Buffer.alloc(archive.length));
            if (failure === 'too-large') return { body: response(Buffer.alloc(archive.length + 1)).body };
            return { body: response(archive.subarray(0, 3)).body };
        };
        const updater = new SoftwareUpdater('1.2.0', directory, () => {}, () => assert.fail('must not reveal'), async () => {}, transport);
        await updater.check();
        assert.equal((await updater.download()).status, 'error');
        assert.equal(updater.state.canDownload, true);
        assert.deepEqual(await files(directory), []);
    }));
}
test('download directory failure cancels an unconsumed HTTP response', async () => fixture(async directory => {
    const blocked = path.join(directory, 'not-a-directory');
    await writeFile(blocked, 'fixture');
    let cancelled = false;
    const updater = new SoftwareUpdater('1.2.0', blocked, () => {}, () => assert.fail('must not reveal'), async () => {}, async (url, signal) => {
        const result = await fixtureTransport(url, signal);
        if (url.endsWith(`/${archiveName}`)) result.cancel = () => { cancelled = true; };
        return result;
    });
    await updater.check();
    assert.equal((await updater.download()).status, 'error');
    assert.equal(cancelled, true);
}));
test('cancellation cleans partial download and concurrent calls do not duplicate requests', async () => fixture(async directory => {
    let continueDownload!: () => void;
    const wait = new Promise<void>(resolve => { continueDownload = resolve; });
    let requested!: () => void;
    const started = new Promise<void>(resolve => { requested = resolve; });
    let downloads = 0;
    const transport: UpdateTransport = async (url, signal) => {
        if (!url.endsWith(`/${archiveName}`)) return fixtureTransport(url, signal);
        downloads += 1;
        return { body: (async function* () { yield archive.subarray(0, 3); requested(); await wait; signal.throwIfAborted(); yield archive.subarray(3); })() };
    };
    const updater = new SoftwareUpdater('1.2.0', directory, () => {}, () => assert.fail('must not reveal'), async () => {}, transport);
    await updater.check();
    const operation = updater.download();
    await started;
    assert.equal((await updater.download()).status, 'downloading');
    assert.equal((await updater.check()).status, 'downloading');
    updater.cancel(); continueDownload();
    assert.equal((await operation).status, 'available');
    assert.match(updater.state.message, /已取消下载/);
    assert.equal(downloads, 1);
    assert.deepEqual(await files(directory), []);
}));
test('network errors remain visible and recover on manual retry', async () => fixture(async directory => {
    let offline = true;
    const updater = new SoftwareUpdater('1.2.0', directory, () => {}, () => {}, async () => {}, async (url, signal) => {
        if (offline) throw new Error('Network fixture unavailable');
        return fixtureTransport(url, signal);
    });
    assert.equal((await updater.check()).status, 'error');
    assert.equal(updater.state.canDownload, false);
    offline = false;
    assert.equal((await updater.check()).status, 'available');
}));

test('GitHub asset digest disagreement fails before downloading the archive', async () => fixture(async directory => {
    const value = metadata(); value.assets[0].digest = `sha256:${'0'.repeat(64)}`;
    let archives = 0;
    const updater = new SoftwareUpdater('1.2.0', directory, () => {}, () => assert.fail('must not reveal'), async () => {}, async (url, signal) => {
        if (url === latestReleaseURL) return response(Buffer.from(JSON.stringify(value)));
        if (url.endsWith(`/${archiveName}`)) archives += 1;
        return fixtureTransport(url, signal);
    });
    await updater.check();
    assert.equal((await updater.download()).status, 'error');
    assert.equal(archives, 0);
    assert.deepEqual(await files(directory), []);
}));

test('Windows download marker writes only the Internet Zone ADS with official source and exclusive creation', async () => {
    const writes: unknown[] = [];
    const source = `${base}/releases/download/v${version}/${archiveName}`;
    await markInternetDownload('C:\\Downloads\\new.zip', source, 'win32', async (...args) => { writes.push(args); });
    assert.deepEqual(writes, [['C:\\Downloads\\new.zip:Zone.Identifier', `[ZoneTransfer]\r\nZoneId=3\r\nHostUrl=${source}\r\n`, { encoding: 'utf8', flag: 'wx' }]]);
    await markInternetDownload('/tmp/new.zip', source, 'darwin', async () => assert.fail('Mac preview must not create a colon file'));
    await markInternetDownload('/tmp/new.zip', source, 'linux', async () => assert.fail('Non-Windows must not write an ADS'));
    await assert.rejects(markInternetDownload('C:\\new.zip', `${source}\r\nZoneId=0`, 'win32', async () => assert.fail('must reject injected source')));
    await assert.rejects(markInternetDownload('C:\\new.zip', 'https://evil.test/new.zip', 'win32', async () => assert.fail('must reject untrusted source')));
    await assert.rejects(markInternetDownload('C:\\new.zip', source, 'win32', async () => { throw new Error('NTFS ADS unavailable'); }));
});

test('download marker runs only after full verification and rename, before ready is visible', async () => fixture(async directory => {
    let marked = false;
    const updater = new SoftwareUpdater('1.2.0', directory, state => {
        if (state.status === 'ready') assert.equal(marked, true);
    }, () => {}, async () => {}, fixtureTransport, async (file, source) => {
        assert.equal(updater.state.status, 'downloading');
        assert.equal(path.basename(file), archiveName);
        assert.equal(source, `${base}/releases/download/v${version}/${archiveName}`);
        assert.deepEqual(await readFile(file), archive);
        assert.deepEqual(await files(directory), [file]);
        marked = true;
    });
    await updater.check();
    assert.equal((await updater.download()).status, 'ready');
    assert.equal(marked, true);
}));

test('marker failure retracts only its newly downloaded ZIP and keeps update recovery available', async () => fixture(async directory => {
    const original = path.join(directory, 'existing-user-file.zip');
    await writeFile(original, 'preserved existing user file');
    const updater = new SoftwareUpdater('1.2.0', directory, () => {}, () => assert.fail('must not reveal unmarked ZIP'), async () => {}, fixtureTransport,
        async () => { throw new Error('FAT volume does not support ADS'); });
    await updater.check();
    assert.equal((await updater.download()).status, 'error');
    assert.equal(updater.state.canDownload, true);
    assert.match(updater.state.message, /来源标记.*浏览器下载/);
    updater.showDownload();
    assert.deepEqual(await files(directory), [original]);
    assert.equal(await readFile(original, 'utf8'), 'preserved existing user file');
}));

test('corrupt archive never reaches the download marker', async () => fixture(async directory => {
    const updater = new SoftwareUpdater('1.2.0', directory, () => {}, () => assert.fail('must not reveal'), async () => {}, async (url, signal) => {
        if (url.endsWith(`/${archiveName}`)) return response(Buffer.alloc(archive.length));
        return fixtureTransport(url, signal);
    }, async () => assert.fail('must not mark before checksum succeeds'));
    await updater.check();
    assert.equal((await updater.download()).status, 'error');
    assert.deepEqual(await files(directory), []);
}));
