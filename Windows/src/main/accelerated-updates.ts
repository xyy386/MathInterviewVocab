import { createHash, randomUUID } from 'node:crypto';
import { createReadStream } from 'node:fs';
import { lstat, mkdir, open, readFile, realpath, rename, unlink, writeFile } from 'node:fs/promises';
import path from 'node:path';
import type { UpdateState } from '../shared/update';
import { markInternetDownload, newerVersion, verifyExecutable, type DownloadMarker } from './updates';
import { verifyInstallerManifest, type SignedInstaller } from './update-signature';
import { assetCandidates, assetURL, normalizeRouteMode, raceIndices, responseRange, routedTransport, smallResponse,
    updateCacheLimit, updateRepository, verifyUpdateIndex, type DesktopIndex, type IndexAsset, type RouteMode,
    type RoutedTransport, type SignedIndex } from './update-routes';

interface Options {
    transport?: RoutedTransport;
    indexPublicKey?: string;
    installerPublicKey?: string;
    markDownload?: DownloadMarker;
    launch?: (file: string) => Promise<void>;
    now?: () => number;
    hedgeDelay?: number;
    slowGraceMs?: number;
    slowDurationMs?: number;
    slowBytesPerSecond?: number;
}
const sha256 = (bytes: Buffer) => createHash('sha256').update(bytes).digest('hex');

/** 应用级单任务下载器；缓存只包含经发布密钥绑定的索引和一个安装包。 */
export class AcceleratedUpdater {
    state: UpdateState;
    private candidate: SignedIndex | null = null;
    private cached: SignedIndex | null = null;
    private installer: Readonly<SignedInstaller> | null = null;
    private directory: string;
    private controller: AbortController | null = null;
    private operation: Promise<UpdateState> | null = null;
    private preferredRoute = '';
    private knownVersion: string;
    private restoring: Promise<void> | null = null;
    private canonicalized = false;
    private readonly transport: RoutedTransport;
    private readonly now: () => number;

    constructor(
        currentVersion: string, cacheDirectory: string,
        private readonly changed: (state: UpdateState) => void,
        private readonly reveal: (file: string) => void,
        private readonly openPage: (url: string) => Promise<unknown>,
        private readonly options: Options = {},
    ) {
        this.directory = path.resolve(cacheDirectory);
        this.knownVersion = currentVersion;
        this.transport = options.transport ?? routedTransport;
        this.now = options.now ?? (() => performance.now());
        this.state = { currentVersion, status: 'idle', message: '手动检查更新，学习和发音始终离线。', canDownload: false,
            routeMode: 'auto', speedBytesPerSecond: 0, canResume: false, hasCachedDownload: false };
    }

    get busy(): boolean { return Boolean(this.operation) || this.state.status === 'installing'; }
    get needsCacheReplacement(): boolean {
        return Boolean(this.cached && this.candidate && !this.same(this.cached, this.candidate));
    }
    private file(name: 'preferences.json' | 'transfer.json' | 'archive.partial' | 'installer.exe'): string {
        return path.join(this.directory, name);
    }
    private publish(patch: Partial<UpdateState>): UpdateState {
        this.state = { ...this.state, ...patch }; this.changed(this.state); return this.state;
    }
    private same(a: SignedIndex, b: SignedIndex): boolean {
        const left = this.archive(a.index); const right = this.archive(b.index);
        return a.index.version === b.index.version && left.bytes === right.bytes && left.sha256 === right.sha256;
    }
    private archive(index: DesktopIndex): IndexAsset { return index.assets.find(asset => asset.name.endsWith('.exe'))!; }
    private async safeDirectory(): Promise<void> {
        await mkdir(this.directory, { recursive: true });
        const stat = await lstat(this.directory);
        if (!stat.isDirectory() || stat.isSymbolicLink()) throw new Error('更新缓存目录不是普通目录。');
        const canonical = await realpath(this.directory);
        // 初次规范化允许 Windows 8.3 别名；随后每次均检查是否被重定向。
        if (this.canonicalized && canonical !== this.directory) throw new Error('更新缓存目录发生变化。');
        this.directory = canonical; this.canonicalized = true;
    }
    private async regular(name: Parameters<AcceleratedUpdater['file']>[0], maximum: number): Promise<number | null> {
        try {
            const file = this.file(name); const stat = await lstat(file);
            if (!stat.isFile() || stat.isSymbolicLink() || stat.size > maximum || await realpath(file) !== file) throw new Error('更新缓存被修改或超出大小。');
            return stat.size;
        } catch (error) { if ((error as NodeJS.ErrnoException).code === 'ENOENT') return null; throw error; }
    }
    private async atomic(name: 'transfer.json' | 'preferences.json', value: unknown): Promise<void> {
        await this.safeDirectory();
        const temporary = path.join(this.directory, `${name}.${randomUUID()}.tmp`);
        try {
            const handle = await open(temporary, 'wx', 0o600);
            try { await handle.writeFile(JSON.stringify(value) + '\n'); await handle.sync(); }
            finally { await handle.close(); }
            await rename(temporary, this.file(name));
        } finally { await unlink(temporary).catch(() => {}); }
    }
    private async preferences(): Promise<void> {
        await this.atomic('preferences.json', { schema: 1, routeMode: this.state.routeMode, knownVersion: this.knownVersion });
    }
    private async digest(file: string, expected: number, signal?: AbortSignal): Promise<string> {
        const hash = createHash('sha256'); let count = 0;
        for await (const chunk of createReadStream(file)) {
            signal?.throwIfAborted(); count += chunk.length;
            if (count > expected) throw new Error('更新缓存大小发生变化。'); hash.update(chunk);
        }
        if (count !== expected) throw new Error('更新缓存不完整。');
        return hash.digest('hex');
    }

    restore(): Promise<void> {
        if (this.restoring) return this.restoring;
        this.restoring = Promise.resolve().then(async () => {
            await this.safeDirectory();
            if (await this.regular('preferences.json', 4096) !== null) {
                const prefs: unknown = JSON.parse(await readFile(this.file('preferences.json'), 'utf8'));
                if (prefs && typeof prefs === 'object') {
                    const value = prefs as Record<string, unknown>;
                    this.state.routeMode = normalizeRouteMode(value.routeMode);
                    if (typeof value.knownVersion === 'string' && newerVersion(value.knownVersion, this.knownVersion)) this.knownVersion = value.knownVersion;
                }
            }
            if (await this.regular('transfer.json', 128 * 1024) === null) return;
            const record: unknown = JSON.parse(await readFile(this.file('transfer.json'), 'utf8'));
            if (!record || typeof record !== 'object') throw new Error('下载缓存信息无效，可清除后重试。');
            const value = record as Record<string, unknown>;
            if (value.schema !== 1 || typeof value.index !== 'string' || typeof value.signature !== 'string') throw new Error('下载缓存信息无效。');
            const signed = verifyUpdateIndex(Buffer.from(value.index, 'base64'), Buffer.from(value.signature, 'base64'), this.options.indexPublicKey);
            if (!newerVersion(signed.index.version, this.state.currentVersion)) {
                await this.removePayload(); return;
            }
            this.cached = signed; this.candidate = signed;
            const asset = this.archive(signed.index);
            const partial = await this.regular('archive.partial', asset.bytes);
            const final = await this.regular('installer.exe', asset.bytes);
            // 已完成文件也重新计算摘要；保存的“ready”标志不具备信任。
            if (final === asset.bytes && await this.digest(this.file('installer.exe'), asset.bytes) === asset.sha256) {
                this.publish({ status: 'paused', message: '已找到完整缓存，点击继续重新验证安装清单。', downloadedBytes: final });
            } else {
                if (final !== null) await unlink(this.file('installer.exe'));
                this.publish({ status: 'paused', message: '已保留下载断点，点击继续；不会自动联网。', downloadedBytes: partial ?? 0 });
            }
            this.publish({ version: signed.index.version, notes: signed.index.notes, totalBytes: asset.bytes,
                fileName: asset.name, canResume: true, canDownload: true, hasCachedDownload: true });
        }).catch(error => {
            this.publish({ status: 'error', message: `无法读取更新缓存：${error instanceof Error ? error.message : '请清除缓存后重试。'}`,
                hasCachedDownload: true, canResume: false, canDownload: false });
        });
        return this.restoring;
    }

    async setRouteMode(input: unknown): Promise<UpdateState> {
        await this.restore();
        if (this.busy) return this.state;
        this.publish({ routeMode: normalizeRouteMode(input), activeRouteLabel: undefined });
        try { await this.preferences(); }
        catch { this.publish({ status: 'error', message: '无法保存更新线路偏好，请检查缓存目录权限。' }); }
        return this.state;
    }

    private run(work: (signal: AbortSignal) => Promise<UpdateState>, timeout: number): Promise<UpdateState> {
        if (this.busy) return Promise.resolve(this.state);
        const controller = new AbortController(); this.controller = controller;
        const signal = AbortSignal.any([controller.signal, AbortSignal.timeout(timeout)]);
        const operation = Promise.resolve().then(() => work(signal)).catch(error => {
            const paused = controller.signal.aborted;
            return this.publish({ status: paused && this.cached ? 'paused' : paused ? 'idle' : 'error',
                message: paused ? '下载已暂停，断点已保留；可换线后继续。' : error instanceof Error ? error.message : '更新失败，可换线重试。',
                canDownload: Boolean(this.candidate), canResume: Boolean(this.cached), speedBytesPerSecond: 0 });
        }).finally(() => {
            if (this.operation === operation) { this.operation = null; this.controller = null; }
        });
        this.operation = operation; return operation;
    }

    async check(): Promise<UpdateState> {
        await this.restore();
        return this.run(async signal => {
            this.publish({ status: 'checking', message: '正在探测并验证更新线路…', canDownload: false, speedBytesPerSecond: 0 });
            const { signed, route } = await raceIndices(this.state.routeMode ?? 'auto', signal, this.transport,
                this.options.indexPublicKey, this.state.currentVersion, this.knownVersion, this.options.hedgeDelay);
            signal.throwIfAborted();
            this.knownVersion = signed.index.version; await this.preferences(); signal.throwIfAborted();
            this.preferredRoute = route.id;
            if (!newerVersion(signed.index.version, this.state.currentVersion)) {
                return this.publish({ status: 'current', message: '当前已是最新 Windows 正式版。', canDownload: false, activeRouteLabel: route.label });
            }
            this.candidate = signed;
            const resume = Boolean(this.cached && this.same(this.cached, signed));
            return this.publish({ status: resume ? 'paused' : 'available', version: signed.index.version, notes: signed.index.notes,
                message: resume ? '发现已保留的下载，可继续。' : `发现 Windows ${signed.index.version}，可手动下载。`,
                totalBytes: this.archive(signed.index).bytes, downloadedBytes: resume ? this.state.downloadedBytes : 0,
                canDownload: true, canResume: resume, activeRouteLabel: route.label });
        }, 45_000);
    }

    private async fetchAsset(signed: SignedIndex, asset: IndexAsset, signal: AbortSignal): Promise<Buffer> {
        let failure: unknown;
        for (const { route, url } of assetCandidates(assetURL(signed.index, asset.name), this.state.routeMode ?? 'auto', this.preferredRoute)) {
            signal.throwIfAborted();
            try {
                const bytes = await smallResponse(await this.transport(url, signal), signal, 4096, asset.bytes);
                if (sha256(bytes) !== asset.sha256) throw new Error('元数据与签名索引摘要不一致。');
                this.publish({ activeRouteLabel: route.label }); return bytes;
            } catch (error) { signal.throwIfAborted(); failure = error; }
        }
        throw failure ?? new Error('无法读取签名安装清单。');
    }

    async download(replaceCache = false): Promise<UpdateState> {
        await this.restore();
        if (!this.candidate || !newerVersion(this.candidate.index.version, this.state.currentVersion)) return this.state;
        if (this.needsCacheReplacement && !replaceCache) return this.publish({ message: '开始新版下载前，请确认清除旧版断点。' });
        const signed = this.candidate;
        return this.run(async signal => {
            await this.safeDirectory(); signal.throwIfAborted();
            this.publish({ status: 'downloading', message: '正在验证安装清单…', canDownload: false, canResume: false, speedBytesPerSecond: 0 });
            const manifestAsset = signed.index.assets.find(asset => asset.name === 'Windows-update.json')!;
            const signatureAsset = signed.index.assets.find(asset => asset.name === 'Windows-update.json.sig')!;
            const manifest = await this.fetchAsset(signed, manifestAsset, signal);
            const signature = await this.fetchAsset(signed, signatureAsset, signal);
            const archive = this.archive(signed.index);
            const installer = verifyInstallerManifest(manifest, signature, { version: signed.index.version, fileName: archive.name,
                bytes: archive.bytes, sha256: archive.sha256 }, this.options.installerPublicKey);
            if (this.needsCacheReplacement) await this.removePayload();
            this.installer = installer;
            this.cached = signed;
            await this.atomic('transfer.json', { schema: 1, index: signed.bytes.toString('base64'), signature: signed.signature.toString('base64') });
            this.publish({ hasCachedDownload: true, version: signed.index.version, notes: signed.index.notes,
                fileName: archive.name, totalBytes: archive.bytes });
            if (await this.regular('installer.exe', archive.bytes) === archive.bytes
                && await this.digest(this.file('installer.exe'), archive.bytes, signal) === archive.sha256) {
                return await this.ready(signed, archive);
            }
            const routes = assetCandidates(assetURL(signed.index, archive.name), this.state.routeMode ?? 'auto', this.preferredRoute);
            let failure: unknown;
            for (let index = 0; index < routes.length; index += 1) {
                const { route, url } = routes[index];
                signal.throwIfAborted();
                let offset = await this.regular('archive.partial', archive.bytes) ?? 0;
                if (offset === archive.bytes) {
                    if (await this.digest(this.file('archive.partial'), archive.bytes, signal) === archive.sha256) break;
                    await unlink(this.file('archive.partial')); offset = 0;
                }
                this.publish({ activeRouteLabel: route.label, downloadedBytes: offset, message: offset ? `正在通过 ${route.label} 断点续传…` : `正在通过 ${route.label} 下载更新…` });
                let response;
                try {
                    response = await this.transport(url, signal, offset);
                    const start = responseRange(response, offset, archive.bytes);
                    if (offset && !start) this.publish({ downloadedBytes: 0, message: '此线路不支持续传，正在安全重新下载。' });
                    const handle = await open(this.file('archive.partial'), start ? 'a' : 'w', 0o600);
                    let count = start; let windowBytes = 0; let speed = 0;
                    const began = this.now(); let windowStart = began; let lastPublished = began; let lowSince: number | null = null;
                    try {
                        for await (const chunk of response.body) {
                            signal.throwIfAborted(); count += chunk.byteLength; windowBytes += chunk.byteLength;
                            if (count > archive.bytes || count > updateCacheLimit) throw new Error('安装包超过签名声明大小。');
                            await handle.writeFile(chunk);
                            const now = this.now();
                            if (now - windowStart >= 1000) {
                                speed = Math.floor(windowBytes * 1000 / Math.max(1, now - windowStart));
                                windowBytes = 0; windowStart = now;
                                if (now - began >= (this.options.slowGraceMs ?? 3000) && index < routes.length - 1) {
                                    if (speed < (this.options.slowBytesPerSecond ?? 40 * 1024)) {
                                        lowSince ??= now;
                                        if (now - lowSince >= (this.options.slowDurationMs ?? 8000)) throw new Error('当前线路持续低速，正在切换线路续传。');
                                    } else lowSince = null;
                                }
                            }
                            if (now - lastPublished >= 200) { this.publish({ downloadedBytes: count, speedBytesPerSecond: speed }); lastPublished = now; }
                        }
                        signal.throwIfAborted();
                        if (count !== archive.bytes) throw new Error('下载中断，已保留断点，可继续。');
                    } finally { try { await handle.sync(); } finally { await handle.close(); } }
                    this.publish({ status: 'verifying', message: '正在校验完整安装包…', downloadedBytes: count, speedBytesPerSecond: 0 });
                    if (await this.digest(this.file('archive.partial'), archive.bytes, signal) !== archive.sha256) {
                        await unlink(this.file('archive.partial')); throw new Error('安装包摘要不一致，已移除损坏内容。');
                    }
                    break;
                } catch (error) {
                    signal.throwIfAborted(); failure = error;
                    if (index === routes.length - 1) throw error;
                    this.publish({ status: 'downloading', message: '正在切换线路，并保留已下载内容…', speedBytesPerSecond: 0 });
                } finally { response?.cancel(); }
            }
            signal.throwIfAborted();
            if (await this.regular('archive.partial', archive.bytes) !== archive.bytes
                || await this.digest(this.file('archive.partial'), archive.bytes, signal) !== archive.sha256) throw failure ?? new Error('安装包未完整下载。');
            await rename(this.file('archive.partial'), this.file('installer.exe'));
            signal.throwIfAborted();
            return await this.ready(signed, archive);
        }, 15 * 60_000);
    }

    private async ready(signed: SignedIndex, archive: IndexAsset): Promise<UpdateState> {
        const source = assetURL(signed.index, archive.name);
        let marked = false;
        if (!this.options.markDownload && process.platform === 'win32') {
            try {
                const zone = await readFile(`${this.file('installer.exe')}:Zone.Identifier`, 'utf8');
                if (!/(?:^|\r?\n)ZoneId=3(?:\r?\n|$)/.test(zone) || !zone.split(/\r?\n/).includes(`HostUrl=${source}`)) {
                    throw new Error('缓存安装包的互联网来源标记无效。');
                }
                marked = true;
            } catch (error) { if ((error as NodeJS.ErrnoException).code !== 'ENOENT') throw error; }
        }
        if (!marked) await (this.options.markDownload ?? markInternetDownload)(this.file('installer.exe'), source);
        return this.publish({ status: 'ready', message: '签名与完整文件校验通过，可确认安装并重启。', downloadedBytes: archive.bytes,
            canDownload: false, canResume: false, speedBytesPerSecond: 0, hasCachedDownload: true });
    }
    cancel(): UpdateState { this.controller?.abort(); return this.state; }
    async pause(): Promise<UpdateState> { this.cancel(); if (this.operation) await this.operation; return this.state; }
    private async removePayload(): Promise<void> {
        for (const name of ['archive.partial', 'installer.exe', 'transfer.json'] as const) {
            // 固定文件名可以安全 unlink 符号链接本身；绝不递归删除其目标或目录。
            await unlink(this.file(name)).catch(error => { if ((error as NodeJS.ErrnoException).code !== 'ENOENT') throw error; });
        }
        this.cached = null; this.installer = null;
    }
    async clearCache(): Promise<UpdateState> {
        await this.restore(); if (this.busy) return this.state;
        try {
            await this.safeDirectory(); await this.removePayload();
            return this.publish({ status: this.candidate ? 'available' : 'idle', downloadedBytes: 0, canResume: false,
                hasCachedDownload: false, canDownload: Boolean(this.candidate), message: '下载缓存已清除，学习记录与设置未改变。' });
        } catch (error) { return this.publish({ status: 'error', message: `无法清除缓存：${String(error)}` }); }
    }
    async install(): Promise<UpdateState> {
        if (this.busy || this.state.status !== 'ready' || !this.installer || !this.options.launch) return this.state;
        const installer = this.installer;
        this.publish({ status: 'installing', message: '正在重新校验安装文件并交接安装器…' });
        try {
            await this.safeDirectory();
            await verifyExecutable(this.file('installer.exe'), installer, this.state.currentVersion);
            await this.options.launch(this.file('installer.exe'));
        } catch (error) {
            this.installer = null;
            this.publish({ status: 'error', canDownload: true, canResume: true, message: error instanceof Error ? error.message : '安装器交接失败，应用尚未退出。' });
        }
        return this.state;
    }
    showDownload(): void { if (this.state.status === 'ready') this.reveal(this.file('installer.exe')); }
    async showRelease(): Promise<void> { if (this.candidate) await this.openPage(`${updateRepository}/releases/tag/v${this.candidate.index.version}`); }
}
