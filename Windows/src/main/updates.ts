import { createHash } from 'node:crypto';
import { createReadStream } from 'node:fs';
import { get } from 'node:https';
import { lstat, mkdir, mkdtemp, open, realpath, rename, unlink, writeFile } from 'node:fs/promises';
import path from 'node:path';
import type { UpdateState } from '../shared/update';
import { verifyInstallerManifest, type SignedInstaller } from './update-signature';

// 保留既有内部导出名；按平台筛选发布列表，不占用 Android 的全局 Latest。
export const latestReleaseURL = 'https://api.github.com/repos/xyy386/MathInterviewVocab/releases?per_page=50';
const repositoryURL = 'https://github.com/xyy386/MathInterviewVocab';
export const maximumArchiveBytes = 500 * 1024 * 1024;
const allowedHosts = new Set(['api.github.com', 'github.com', 'release-assets.githubusercontent.com', 'objects.githubusercontent.com']);

interface ReleaseAsset { name: string; url: string; bytes: number; sha256?: string }
export interface UpdateRelease {
    version: string;
    notes: string;
    pageURL: string;
    archive: ReleaseAsset;
    checksums: ReleaseAsset;
    manifest: ReleaseAsset;
    signature: ReleaseAsset;
}
export interface UpdateResponse {
    body: AsyncIterable<Uint8Array>;
    contentLength?: number;
    cancel?: () => void;
}
export type UpdateTransport = (url: string, signal: AbortSignal) => Promise<UpdateResponse>;
export type DownloadMarker = (file: string, source: string) => Promise<void>;
type ZoneWriter = (file: string, content: string, options: { encoding: 'utf8'; flag: 'wx' }) => Promise<void>;

// 仅为本次新建的 Windows 下载包添加 Internet Zone 标记，不移除任何系统保护。
export async function markInternetDownload(
    file: string,
    source: string,
    platform: NodeJS.Platform = process.platform,
    writer: ZoneWriter = writeFile,
): Promise<void> {
    if (platform !== 'win32') return;
    const url = trustedURL(source);
    if (!source.startsWith(`${repositoryURL}/releases/download/`) || url.search || /[\r\n]/.test(source)) {
        throw new Error('更新来源标记地址无效。');
    }
    await writer(`${file}:Zone.Identifier`, `[ZoneTransfer]\r\nZoneId=3\r\nHostUrl=${source}\r\n`, { encoding: 'utf8', flag: 'wx' });
}

export function versionParts(version: string): number[] {
    if (!/^(0|[1-9]\d*)\.(0|[1-9]\d*)\.(0|[1-9]\d*)$/.test(version)) throw new Error('版本号格式不受支持。');
    const parts = version.split('.').map(Number);
    if (parts.some(value => !Number.isSafeInteger(value))) throw new Error('版本号超出支持范围。');
    return parts;
}

export function newerVersion(candidate: string, installed: string): boolean {
    const left = versionParts(candidate);
    const right = versionParts(installed);
    for (let index = 0; index < 3; index += 1) {
        if (left[index] !== right[index]) return left[index] > right[index];
    }
    return false;
}

export function trustedURL(input: string): URL {
    const url = new URL(input);
    if (url.protocol !== 'https:' || !allowedHosts.has(url.hostname) || url.username || url.password || url.port || url.hash) {
        throw new Error('更新地址不属于受信任的 GitHub 下载服务。');
    }
    return url;
}

export function redirectedURL(location: string, previous: string, redirects: number): string {
    if (redirects >= 5) throw new Error('更新下载重定向次数过多。');
    return trustedURL(new URL(location, trustedURL(previous)).toString()).toString();
}

function object(input: unknown): Record<string, unknown> {
    if (!input || typeof input !== 'object' || Array.isArray(input)) throw new Error('发布信息格式无效。');
    return input as Record<string, unknown>;
}

export function releaseMetadata(input: unknown): { version: string; pageURL: string; notes: string } {
    const release = object(input);
    if (release.draft !== false || release.prerelease !== false || typeof release.tag_name !== 'string' || !release.tag_name.startsWith('v')) {
        throw new Error('仅支持 GitHub 正式发布版本。');
    }
    const version = release.tag_name.slice(1);
    versionParts(version);
    const pageURL = `${repositoryURL}/releases/tag/v${version}`;
    if (release.html_url !== pageURL) throw new Error('发布信息与官方仓库不一致。');
    return { version, pageURL, notes: typeof release.body === 'string' ? release.body.slice(0, 8000) : '' };
}

export function parseRelease(input: unknown): UpdateRelease {
    const release = object(input);
    const metadata = releaseMetadata(input);
    const { version } = metadata;
    if (!Array.isArray(release.assets)) throw new Error('发布附件格式无效。');
    const asset = (name: string, limit: number): ReleaseAsset => {
        const matches = (release.assets as unknown[]).map(object).filter(value => value.name === name);
        if (matches.length !== 1) throw new Error(`发布附件缺失或重复：${name}`);
        const value = matches[0];
        const expectedURL = `${repositoryURL}/releases/download/v${version}/${name}`;
        if (value.state !== 'uploaded' || value.browser_download_url !== expectedURL || !Number.isSafeInteger(value.size) || Number(value.size) <= 0 || Number(value.size) > limit) {
            throw new Error('发布附件地址或大小无效。');
        }
        if (value.digest !== undefined && value.digest !== null && (typeof value.digest !== 'string' || !/^sha256:[a-f0-9]{64}$/.test(value.digest))) {
            throw new Error('发布附件摘要格式无效。');
        }
        trustedURL(expectedURL);
        return { name, url: expectedURL, bytes: Number(value.size), sha256: typeof value.digest === 'string' ? value.digest.slice(7) : undefined };
    };
    return {
        ...metadata,
        archive: asset(`MathInterviewVocab-Windows-x64-Setup-v${version}.exe`, maximumArchiveBytes),
        checksums: asset('SHA256SUMS.txt', 64 * 1024),
        manifest: asset('Windows-update.json', 4096),
        signature: asset('Windows-update.json.sig', 1024),
    };
}

export function newestWindowsRelease(input: unknown): UpdateRelease | null {
    if (!Array.isArray(input) || input.length > 50) throw new Error('GitHub 发布列表格式无效。');
    let latest: UpdateRelease | null = null;
    for (const item of input) {
        const release = object(item);
        if (release.draft !== false || release.prerelease !== false || typeof release.tag_name !== 'string'
            || !/^v\d+\.\d+\.\d+$/.test(release.tag_name) || !Array.isArray(release.assets)) continue;
        const installer = `MathInterviewVocab-Windows-x64-Setup-${release.tag_name}.exe`;
        if (!release.assets.some(asset => object(asset).name === installer)) continue;
        // 出现本平台安装包就必须通过完整元数据校验，不能悄悄略过坏签名附件。
        const candidate = parseRelease(item);
        if (!latest || newerVersion(candidate.version, latest.version)) latest = candidate;
    }
    return latest;
}

export function checksumFor(text: string, name: string): string {
    const values = new Map<string, string>();
    for (const line of text.split(/\r?\n/)) {
        if (!line.trim()) continue;
        const match = /^([a-fA-F0-9]{64}) [ *]([A-Za-z0-9][A-Za-z0-9._-]*)$/.exec(line);
        if (!match || values.has(match[2])) throw new Error('校验和文件格式无效或包含重复附件。');
        values.set(match[2], match[1].toLowerCase());
    }
    const checksum = values.get(name);
    if (!checksum) throw new Error('校验和文件未收录此 Windows 更新包。');
    return checksum;
}

function responseError(status: number): Error {
    if (status === 403 || status === 429) return new Error('GitHub 暂时限制请求，请稍后重试。');
    if (status === 404) return new Error('未找到正式发布版本或更新附件，请稍后重试。');
    return new Error(`GitHub 更新请求失败（HTTP ${status}）。`);
}

// 所有网络请求仅在主进程发生，不携带账号、Cookie 或本机授权凭据。
export const githubTransport: UpdateTransport = (input, signal) => {
    const requestURL = async (url: string, redirects: number): Promise<UpdateResponse> => {
        signal.throwIfAborted();
        const target = trustedURL(url);
        return new Promise((resolve, reject) => {
            const request = get(target, { signal, headers: {
                'User-Agent': 'MathInterviewVocab-Updater', Accept: 'application/vnd.github+json', 'Accept-Encoding': 'identity',
            } }, response => {
                const status = response.statusCode ?? 0;
                if ([301, 302, 303, 307, 308].includes(status)) {
                    response.destroy();
                    try {
                        const location = response.headers.location;
                        if (!location) throw new Error('GitHub 重定向缺少下载地址。');
                        resolve(requestURL(redirectedURL(location, url, redirects), redirects + 1));
                    } catch (error) { reject(error); }
                    return;
                }
                if (status !== 200) { response.destroy(); reject(responseError(status)); return; }
                if (response.headers['content-encoding'] && response.headers['content-encoding'] !== 'identity') {
                    response.destroy(); reject(new Error('更新服务器返回不受支持的压缩传输。')); return;
                }
                const length = response.headers['content-length'];
                if (length && !/^\d+$/.test(length)) { response.destroy(); reject(new Error('更新响应大小无效。')); return; }
                resolve({ body: response, contentLength: length ? Number(length) : undefined, cancel: () => response.destroy() });
            });
            request.setTimeout(30_000, () => request.destroy(new Error('更新连接超时，请检查网络后重试。')));
            request.on('error', reject);
        });
    };
    return requestURL(input, 0);
};

export async function readBounded(response: UpdateResponse, maximum: number, signal: AbortSignal, expected?: number): Promise<Buffer> {
    try {
        if (response.contentLength !== undefined && (response.contentLength > maximum || (expected !== undefined && response.contentLength !== expected))) {
            throw new Error('更新响应大小与发布信息不一致。');
        }
        const chunks: Buffer[] = [];
        let bytes = 0;
        for await (const chunk of response.body) {
            signal.throwIfAborted();
            bytes += chunk.byteLength;
            if (bytes > maximum) throw new Error('更新响应超过允许大小。');
            chunks.push(Buffer.from(chunk));
        }
        signal.throwIfAborted();
        if (expected !== undefined && bytes !== expected) throw new Error('更新响应未完整下载，请重试。');
        return Buffer.concat(chunks);
    } catch (error) { response.cancel?.(); throw error; }
}

export class SoftwareUpdater {
    state: UpdateState;
    private release: UpdateRelease | null = null;
    private controller: AbortController | null = null;
    private downloadedPath: string | null = null;
    private signedInstaller: Readonly<SignedInstaller> | null = null;
    private installing = false;

    constructor(
        currentVersion: string,
        private readonly downloadsDirectory: string,
        private readonly changed: (state: UpdateState) => void,
        private readonly reveal: (file: string) => void,
        private readonly openPage: (url: string) => Promise<unknown>,
        private readonly transport: UpdateTransport = githubTransport,
        private readonly markDownload: DownloadMarker = markInternetDownload,
        private readonly installer: { publicKey?: string; launch?: (file: string) => Promise<void> } = {},
    ) {
        versionParts(currentVersion);
        this.state = { currentVersion, status: 'idle', message: '手动检查 GitHub 正式版，不会在后台自动联网。', canDownload: false };
    }

    private publish(patch: Partial<UpdateState>): UpdateState {
        this.state = { ...this.state, ...patch };
        this.changed(this.state);
        return this.state;
    }

    private fail(error: unknown, signal: AbortSignal): UpdateState {
        const cancelled = signal.aborted && signal.reason?.name !== 'TimeoutError';
        const message = cancelled ? '已取消下载。' : signal.aborted ? '更新请求超时，请检查网络后重试。'
            : error instanceof Error ? error.message : '更新失败，请检查网络后重试。';
        return this.publish({ status: cancelled && this.release ? 'available' : 'error', message, canDownload: Boolean(this.release) });
    }

    async check(): Promise<UpdateState> {
        if (this.controller || this.installing) return this.state;
        this.release = null;
        this.downloadedPath = null;
        this.signedInstaller = null;
        const controller = new AbortController();
        this.controller = controller;
        const signal = AbortSignal.any([controller.signal, AbortSignal.timeout(45_000)]);
        this.publish({ status: 'checking', message: '正在检查更新…', canDownload: false, version: undefined, notes: undefined, fileName: undefined });
        try {
            let release: UpdateRelease | null = null;
            for (let page = 1; page <= 4; page += 1) {
                const url = page === 1 ? latestReleaseURL : `${latestReleaseURL}&page=${page}`;
                const bytes = await readBounded(await this.transport(url, signal), 4 * 1024 * 1024, signal);
                const data: unknown = JSON.parse(bytes.toString('utf8'));
                release = newestWindowsRelease(data);
                signal.throwIfAborted();
                if (release || (Array.isArray(data) && data.length < 50)) break;
                if (page === 4) throw new Error('近期发布中未找到 Windows 安装版，请从官方发布页手动确认。');
            }
            if (!release || !newerVersion(release.version, this.state.currentVersion)) {
                return this.publish({ status: 'current', message: release ? '当前已是最新 Windows 版本。' : '暂无可安装的 Windows 新版本。', canDownload: false });
            }
            this.release = release;
            return this.publish({ status: 'available', version: release.version, notes: release.notes, totalBytes: release.archive.bytes,
                message: `发现新版本 ${release.version}。`, canDownload: true });
        } catch (error) { return this.fail(error, signal); }
        finally { this.controller = null; }
    }

    async download(): Promise<UpdateState> {
        if (this.controller || this.installing || !this.release || !this.state.canDownload) return this.state;
        const release = this.release;
        const controller = new AbortController();
        this.controller = controller;
        const signal = AbortSignal.any([controller.signal, AbortSignal.timeout(15 * 60_000)]);
        let partial: string | null = null;
        let unpublishedArchive: string | null = null;
        let activeResponse: UpdateResponse | null = null;
        this.publish({ status: 'downloading', message: '正在下载并校验更新包…', canDownload: false, downloadedBytes: 0 });
        try {
            const checksumText = await readBounded(await this.transport(release.checksums.url, signal), 64 * 1024, signal, release.checksums.bytes);
            if (release.checksums.sha256 && createHash('sha256').update(checksumText).digest('hex') !== release.checksums.sha256) {
                throw new Error('校验和文件与 GitHub 附件摘要不一致。');
            }
            const expectedHash = checksumFor(checksumText.toString('utf8'), release.archive.name);
            if (release.archive.sha256 && release.archive.sha256 !== expectedHash) throw new Error('更新包校验和与 GitHub 附件摘要不一致。');
            const manifest = await readBounded(await this.transport(release.manifest.url, signal), 4096, signal, release.manifest.bytes);
            const signature = await readBounded(await this.transport(release.signature.url, signal), 1024, signal, release.signature.bytes);
            const signedInstaller = verifyInstallerManifest(manifest, signature, {
                version: release.version, fileName: release.archive.name, bytes: release.archive.bytes, sha256: expectedHash,
            }, this.installer.publicKey);
            const response = await this.transport(release.archive.url, signal);
            activeResponse = response;
            if (response.contentLength !== undefined && response.contentLength !== release.archive.bytes) {
                response.cancel?.(); throw new Error('更新包大小与发布信息不一致。');
            }
            const parent = path.join(this.downloadsDirectory, 'MathInterviewVocab Updates');
            await mkdir(parent, { recursive: true });
            const directory = await mkdtemp(path.join(parent, `v${release.version}-`));
            partial = path.join(directory, `${release.archive.name}.partial`);
            const destination = path.join(directory, release.archive.name);
            const file = await open(partial, 'wx');
            const hash = createHash('sha256');
            let bytes = 0;
            let lastPublished = 0;
            try {
                for await (const chunk of response.body) {
                    signal.throwIfAborted();
                    bytes += chunk.byteLength;
                    if (bytes > release.archive.bytes || bytes > maximumArchiveBytes) throw new Error('更新包超过发布大小，下载已停止。');
                    hash.update(chunk);
                    await file.writeFile(chunk);
                    if (Date.now() - lastPublished > 150) {
                        this.publish({ downloadedBytes: bytes }); lastPublished = Date.now();
                    }
                }
                signal.throwIfAborted();
                if (bytes !== release.archive.bytes || hash.digest('hex') !== expectedHash) throw new Error('更新包完整性校验失败，请重新下载。');
                await file.sync();
            } finally { await file.close(); }
            signal.throwIfAborted();
            await rename(partial, destination);
            partial = null;
            unpublishedArchive = destination;
            try { await this.markDownload(destination, release.archive.url); }
            catch {
                try { await unlink(destination); unpublishedArchive = null; }
                catch { throw new Error('无法添加来源标记或移除本次更新包，请勿使用该文件。请点击“查看发布说明”，改用浏览器下载。'); }
                throw new Error('无法添加 Windows 下载来源标记，本次更新包已撤回。请点击“查看发布说明”，改用浏览器下载。');
            }
            signal.throwIfAborted();
            // Windows 临时目录可能使用 8.3／大小写别名；记录经验证文件的真实路径，
            // 后续仍严格拒绝文件或父目录被重定向，而不是放宽安装时的路径校验。
            const canonicalDestination = await realpath(destination);
            signal.throwIfAborted();
            this.downloadedPath = canonicalDestination;
            this.signedInstaller = signedInstaller;
            this.publish({ status: 'ready', message: '下载完成，数字签名与 SHA-256 校验通过。点击“安装并重启”即可更新，学习记录会保留。', downloadedBytes: bytes,
                canDownload: false, fileName: release.archive.name });
            unpublishedArchive = null;
            return this.state;
        } catch (error) { return this.fail(error, signal); }
        finally {
            activeResponse?.cancel?.();
            if (partial) await unlink(partial).catch(() => {});
            if (unpublishedArchive) await unlink(unpublishedArchive).catch(() => {});
            this.controller = null;
        }
    }

    async install(): Promise<UpdateState> {
        if (this.controller || this.installing || this.state.status !== 'ready' || !this.downloadedPath || !this.signedInstaller) return this.state;
        const file = this.downloadedPath;
        const manifest = this.signedInstaller;
        this.installing = true;
        this.publish({ status: 'installing', message: '正在重新校验并启动安装器，随后将退出并重启…', canDownload: false });
        try {
            if (!newerVersion(manifest.version, this.state.currentVersion) || !this.installer.launch) throw new Error('此版本或运行环境不支持安装更新。');
            const stat = await lstat(file);
            if (!stat.isFile() || stat.isSymbolicLink() || stat.size !== manifest.bytes || await realpath(file) !== path.resolve(file)) {
                throw new Error('已下载的安装包发生变化，请重新下载。');
            }
            const hash = createHash('sha256');
            let bytes = 0;
            for await (const chunk of createReadStream(file)) {
                bytes += chunk.length;
                if (bytes > manifest.bytes) throw new Error('安装包大小发生变化，请重新下载。');
                hash.update(chunk);
            }
            if (bytes !== manifest.bytes || hash.digest('hex') !== manifest.sha256) throw new Error('安装包完整性校验失败，已阻止执行。请重新下载。');
            await this.installer.launch(file);
            // 安装器接管后不在仍存活的旧进程内宣称“安装成功”。
            return this.state;
        } catch (error) {
            this.downloadedPath = null;
            this.signedInstaller = null;
            return this.publish({ status: 'error', message: error instanceof Error ? error.message : '安装器启动失败，应用尚未退出。', canDownload: Boolean(this.release) });
        } finally { this.installing = false; }
    }

    cancel(): UpdateState { if (!this.installing) this.controller?.abort(); return this.state; }
    showDownload(): void { if (this.downloadedPath && this.state.status === 'ready') this.reveal(this.downloadedPath); }
    async showRelease(): Promise<void> { if (this.release) await this.openPage(this.release.pageURL); }
}
