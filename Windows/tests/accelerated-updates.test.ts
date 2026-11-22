import assert from 'node:assert/strict';
import test from 'node:test';
import { createHash, generateKeyPairSync, sign } from 'node:crypto';
import { mkdtemp, readFile, readdir, rm, symlink, writeFile } from 'node:fs/promises';
import { tmpdir } from 'node:os';
import path from 'node:path';
import { AcceleratedUpdater } from '../src/main/accelerated-updates';
import { assetCandidates, checkedRouteURL, indexCandidates, normalizeRouteMode, raceIndices, responseRange,
    routedTransport, verifyUpdateIndex, type RoutedResponse, type RoutedTransport } from '../src/main/update-routes';

const keys = generateKeyPairSync('ed25519');
const publicKey = keys.publicKey.export({ type: 'spki', format: 'pem' }).toString();
const hash = (bytes: Buffer) => createHash('sha256').update(bytes).digest('hex');
const payload = Buffer.from('MZ deterministic signed installer fixture 1.5.0');
function fixtureRelease(version = '1.5.0') {
    const name = `MathInterviewVocab-Windows-x64-Setup-v${version}.exe`;
    const manifest = Buffer.from(JSON.stringify({ schema: 1, platform: 'windows-x64', version,
        fileName: name, bytes: payload.length, sha256: hash(payload) }) + '\n');
    const manifestSignature = Buffer.from(sign(null, manifest, keys.privateKey).toString('base64') + '\n');
    const index = { schema: 1, platform: 'windows-x64', version, build: `${version}.7`, notes: '更新说明，不渲染 HTML。', assets: [
        { name, bytes: payload.length, sha256: hash(payload) },
        { name: 'Windows-update.json', bytes: manifest.length, sha256: hash(manifest) },
        { name: 'Windows-update.json.sig', bytes: manifestSignature.length, sha256: hash(manifestSignature) },
    ] };
    const bytes = Buffer.from(JSON.stringify(index) + '\n');
    const signature = Buffer.from(sign(null, bytes, keys.privateKey).toString('base64') + '\n');
    return { index, bytes, signature, manifest, manifestSignature, name };
}
const release = fixtureRelease();
function response(bytes: Buffer, status = 200, contentRange?: string): RoutedResponse {
    return { status, contentRange, contentLength: bytes.length, body: (async function* () { yield bytes; })(), cancel() {} };
}
function transportFor(value = release, archive?: RoutedTransport): RoutedTransport {
    return async (url, signal, offset = 0) => {
        signal.throwIfAborted();
        if (url.endsWith('latest.json.sig')) return response(value.signature);
        if (url.endsWith('latest.json')) return response(value.bytes);
        if (url.endsWith('/Windows-update.json.sig')) return response(value.manifestSignature);
        if (url.endsWith('/Windows-update.json')) return response(value.manifest);
        if (url.endsWith('.exe')) return archive ? archive(url, signal, offset) : response(payload.subarray(offset), offset ? 206 : 200,
            offset ? `bytes ${offset}-${payload.length - 1}/${payload.length}` : undefined);
        throw new Error('unexpected fixture route');
    };
}
async function fixture(run: (directory: string) => Promise<void>) {
    const directory = await mkdtemp(path.join(process.env.MATH_VOCAB_VALIDATION_DIRECTORY ?? tmpdir(), 'vocab-multiline-'));
    try { await run(directory); } finally { await rm(directory, { recursive: true, force: true }); }
}
function updater(directory: string, options: ConstructorParameters<typeof AcceleratedUpdater>[5] = {}) {
    return new AcceleratedUpdater('1.4.0', directory, () => {}, () => assert.fail('no reveal'), async () => {}, {
        transport: transportFor(), indexPublicKey: publicKey, installerPublicKey: publicKey, markDownload: async () => {}, ...options,
    });
}
const waitForAbort = (signal: AbortSignal) => new Promise<never>((_, reject) => {
    if (signal.aborted) reject(signal.reason);
    else signal.addEventListener('abort', () => reject(signal.reason), { once: true });
});

test('signed index binds platform, build, exact asset names, lengths and hashes', () => {
    assert.equal(verifyUpdateIndex(release.bytes, release.signature, publicKey).index.version, '1.5.0');
    assert.throws(() => verifyUpdateIndex(Buffer.concat([release.bytes, Buffer.from(' ')]), release.signature, publicKey));
    for (const change of [
        (value: typeof release.index) => { value.platform = 'android'; },
        (value: typeof release.index) => { value.version = '1.5.0-beta'; },
        (value: typeof release.index) => { value.build = '1.5.0.0'; },
        (value: typeof release.index) => { value.assets[0].name = '../installer.exe'; },
        (value: typeof release.index) => { value.assets[0].bytes = 0; },
        (value: typeof release.index) => { value.assets[0].sha256 = 'invalid'; },
        (value: typeof release.index) => { value.assets[1] = value.assets[0]; },
    ]) {
        const value = structuredClone(release.index); change(value);
        const bytes = Buffer.from(JSON.stringify(value));
        const signature = Buffer.from(sign(null, bytes, keys.privateKey).toString('base64'));
        assert.throws(() => verifyUpdateIndex(bytes, signature, publicKey));
    }
});
test('three modes limit CDN to metadata and direct mode never uses a mirror', () => {
    assert.equal(normalizeRouteMode('unexpected'), 'auto');
    assert.deepEqual(indexCandidates('direct').map(value => value.route.id), ['direct']);
    const url = `https://github.com/xyy386/MathInterviewVocab/releases/download/v1.5.0/${release.name}`;
    assert.deepEqual(assetCandidates(url, 'direct', 'ghfast').map(value => value.route.id), ['direct']);
    assert.equal(assetCandidates(url, 'mirror')[0].route.id, 'ghproxy');
    assert.ok(assetCandidates(url, 'auto').every(value => !value.url.includes('jsdelivr')));
    for (const value of [...indexCandidates('auto'), ...assetCandidates(url, 'auto')]) assert.doesNotThrow(() => checkedRouteURL(value.url));
});
for (const url of [
    'https://ghfast.top/https://github.com/other/repository/releases/download/a.exe',
    'https://gh-proxy.com/https://raw.githubusercontent.com/xyy386/MathInterviewVocab/main/Android/latest-release.json',
    'https://testingcf.jsdelivr.net/gh/xyy386/MathInterviewVocab@main/Windows/resources/audio/file.mp3',
    'https://ghproxy.net/https://github.com/xyy386/MathInterviewVocab/releases/download/v1.5.0/file.exe',
    'http://github.com/xyy386/MathInterviewVocab/file', 'https://user@ghfast.top/file',
    'https://raw.githubusercontent.com/xyy386/MathInterviewVocab/main/updates/windows/latest.json?extra=1',
    'https://release-assets.githubusercontent.com/unbound-download',
]) test(`restricted transport rejects before opening a socket: ${url}`, async () => {
    assert.throws(() => checkedRouteURL(url));
    await assert.rejects(routedTransport(url, new AbortController().signal));
});
test('range validation requires exact Content-Range and never appends a full 200 response', () => {
    assert.equal(responseRange({ status: 206, contentRange: 'bytes 10-99/100', contentLength: 90 }, 10, 100), 10);
    assert.equal(responseRange({ status: 200, contentLength: 100 }, 10, 100), 0);
    for (const bad of [
        { status: 206 }, { status: 206, contentRange: 'bytes 0-99/100' },
        { status: 206, contentRange: 'bytes 10-98/100' }, { status: 206, contentRange: 'bytes 10-99/101' },
        { status: 206, contentRange: 'bytes 10-99/*' }, { status: 206, contentRange: 'bytes 10-99/100', contentLength: 91 },
        { status: 416 }, { status: 200, contentLength: 99 },
    ]) assert.throws(() => responseRange(bad, 10, 100));
});
test('hedged lookup cancels slow official requests after a verified mirror wins', async () => {
    let aborted = 0;
    const transport: RoutedTransport = async (url, signal) => {
        if (url.startsWith('https://raw.')) {
            signal.addEventListener('abort', () => { aborted += 1; }, { once: true });
            await waitForAbort(signal); throw new Error('unreachable');
        }
        return transportFor()(url, signal);
    };
    const result = await raceIndices('auto', new AbortController().signal, transport, publicKey, '1.4.0', '1.4.0', 1);
    assert.equal(result.route.id, 'ghproxy'); assert.equal(aborted, 2);
});
test('stale signed index cannot overwrite a known newer version', async () => {
    await assert.rejects(raceIndices('direct', new AbortController().signal, transportFor(fixtureRelease('1.4.0')),
        publicKey, '1.4.0', '1.5.0', 1), /旧版缓存/);
});
test('restore and preference changes do not perform network requests', async () => fixture(async directory => {
    let requests = 0;
    const first = updater(directory, { transport: async () => { requests += 1; throw new Error('no startup networking'); } });
    await first.restore(); await first.setRouteMode('direct');
    const second = updater(directory, { transport: async () => { requests += 1; throw new Error('no startup networking'); } });
    await second.restore(); assert.equal(second.state.routeMode, 'direct'); assert.equal(requests, 0);
}));
test('download and explicit install share the verified executable gate', async () => fixture(async directory => {
    let launched = 0; let marked = 0;
    const current = updater(directory, { launch: async () => { launched += 1; }, markDownload: async () => { marked += 1; } });
    assert.equal((await current.check()).status, 'available'); assert.equal(launched, 0);
    assert.equal((await current.download()).status, 'ready'); assert.equal(marked, 1); assert.equal(launched, 0);
    await current.install(); assert.equal(launched, 1);
}));
test('pause persists a partial file, restart restores without networking, and resume uses its actual offset', async () => fixture(async directory => {
    let notify!: () => void;
    const received = new Promise<void>(resolve => { notify = resolve; });
    const first = updater(directory, { transport: transportFor(release, async (_url, signal) => ({ status: 200, contentLength: payload.length,
        body: (async function* () { yield payload.subarray(0, 12); notify(); await waitForAbort(signal); })(), cancel() {} })) });
    await first.setRouteMode('direct'); await first.check();
    const downloading = first.download(); await received; await first.pause(); await downloading;
    assert.equal(first.state.status, 'paused'); assert.equal((await readFile(path.join(directory, 'archive.partial'))).length, 12);
    const offsets: number[] = [];
    const second = updater(directory, { transport: transportFor(release, async (_url, _signal, offset = 0) => {
        offsets.push(offset); return response(payload.subarray(offset), 206, `bytes ${offset}-${payload.length - 1}/${payload.length}`);
    }) });
    await second.restore(); assert.equal(second.state.status, 'paused'); assert.deepEqual(offsets, []);
    assert.equal(second.state.downloadedBytes, 12);
    assert.equal((await second.download()).status, 'ready'); assert.deepEqual(offsets, [12]);
    assert.deepEqual(await readFile(path.join(directory, 'installer.exe')), payload);
}));
for (const kind of ['wrong-range', 'missing-range', 'full-response'] as const) {
    test(`resume handles ${kind} without corrupt concatenation`, async () => fixture(async directory => {
        let attempt = 0;
        const current = updater(directory, { transport: transportFor(release, async () => {
            attempt += 1;
            if (attempt === 1) return { status: 200, contentLength: payload.length,
                body: (async function* () { yield payload.subarray(0, 9); throw new Error('disconnected'); })(), cancel() {} };
            return kind === 'full-response' ? response(payload) : response(payload.subarray(9), 206,
                kind === 'wrong-range' ? `bytes 8-${payload.length - 1}/${payload.length}` : undefined);
        }) });
        await current.setRouteMode('direct'); await current.check(); assert.equal((await current.download()).status, 'error');
        const state = await current.download();
        if (kind === 'full-response') {
            assert.equal(state.status, 'ready'); assert.deepEqual(await readFile(path.join(directory, 'installer.exe')), payload);
        } else { assert.equal(state.status, 'error'); assert.equal((await readFile(path.join(directory, 'archive.partial'))).length, 9); }
    }));
}
test('broken route resumes through another mirror in the same operation', async () => fixture(async directory => {
    const offsets: number[] = [];
    const current = updater(directory, { transport: transportFor(release, async (url, _signal, offset = 0) => {
        offsets.push(offset);
        if (url.startsWith('https://github.com')) return { status: 200, contentLength: payload.length,
            body: (async function* () { yield payload.subarray(0, 8); throw new Error('route disconnected'); })(), cancel() {} };
        return response(payload.subarray(offset), 206, `bytes ${offset}-${payload.length - 1}/${payload.length}`);
    }) });
    await current.check(); assert.equal((await current.download()).status, 'ready'); assert.deepEqual(offsets, [0, 8]);
}));
test('sustained slow route switches without downloading the entire file twice', async () => fixture(async directory => {
    let now = 0; const offsets: number[] = [];
    const current = updater(directory, { now: () => now, slowGraceMs: 0, slowDurationMs: 1000, slowBytesPerSecond: 1000,
        transport: transportFor(release, async (url, _signal, offset = 0) => {
            offsets.push(offset);
            if (url.startsWith('https://github.com')) return { status: 200, contentLength: payload.length,
                body: (async function* () { now += 1000; yield payload.subarray(0, 4); now += 1000; yield payload.subarray(4, 8); })(), cancel() {} };
            return response(payload.subarray(offset), 206, `bytes ${offset}-${payload.length - 1}/${payload.length}`);
        }) });
    await current.check(); assert.equal((await current.download()).status, 'ready'); assert.deepEqual(offsets, [0, 8]);
}));
test('wrong signature or hash never produces an installable result', async () => fixture(async directory => {
    let launches = 0;
    const current = updater(directory, { launch: async () => { launches += 1; },
        transport: transportFor(release, async () => response(Buffer.alloc(payload.length, 0))) });
    await current.setRouteMode('direct'); await current.check(); assert.equal((await current.download()).status, 'error');
    await current.install(); assert.equal(launches, 0);
    assert.ok(!(await readdir(directory)).includes('installer.exe'));
    const other = updater(path.join(directory, 'bad-signature'), { transport: async () => response(release.bytes), hedgeDelay: 0 });
    assert.equal((await other.check()).status, 'error');
}));
test('tampering after ready blocks launch and leaves the current application alive', async () => fixture(async directory => {
    let launches = 0;
    const current = updater(directory, { launch: async () => { launches += 1; } });
    await current.check(); await current.download();
    await writeFile(path.join(directory, 'installer.exe'), Buffer.alloc(payload.length, 1));
    assert.equal((await current.install()).status, 'error'); assert.equal(launches, 0);
}));
test('marker failure remains blocked on a complete cached file until marking succeeds', async () => fixture(async directory => {
    let allowed = false;
    const current = updater(directory, { markDownload: async () => { if (!allowed) throw new Error('marker unavailable'); } });
    await current.check(); assert.equal((await current.download()).status, 'error');
    assert.equal((await current.download()).status, 'error');
    allowed = true; assert.equal((await current.download()).status, 'ready');
}));
test('clear cache removes only owned download files and retains unrelated data', async () => fixture(async directory => {
    const current = updater(directory); await current.check(); await current.download();
    await writeFile(path.join(directory, 'progress.json'), 'unrelated sentinel');
    await writeFile(path.join(directory, 'settings.json'), 'unrelated settings');
    await current.clearCache();
    assert.equal(await readFile(path.join(directory, 'progress.json'), 'utf8'), 'unrelated sentinel');
    assert.equal(await readFile(path.join(directory, 'settings.json'), 'utf8'), 'unrelated settings');
    assert.ok(!(await readdir(directory)).includes('installer.exe'));
}));
test('corrupt cached metadata and symlinked payload cannot become executable or escape cache deletion', async () => fixture(async directory => {
    const current = updater(directory); await current.restore();
    const outside = path.join(directory, 'outside.txt'); await writeFile(outside, 'preserve');
    await symlink(outside, path.join(directory, 'installer.exe'));
    await writeFile(path.join(directory, 'transfer.json'), '{broken');
    const next = updater(directory); await next.restore(); assert.equal(next.state.status, 'error');
    await next.install(); await next.clearCache(); assert.equal(await readFile(outside, 'utf8'), 'preserve');
}));
test('new version does not discard an older pending cache without confirmation', async () => fixture(async directory => {
    const current = updater(directory); await current.check(); await current.download();
    const next = updater(directory, { transport: transportFor(fixtureRelease('1.6.0')) });
    await next.restore(); await next.check(); assert.equal(next.needsCacheReplacement, true);
    await next.download(); assert.deepEqual(await readFile(path.join(directory, 'installer.exe')), payload);
    assert.equal((await next.download(true)).status, 'ready');
}));
