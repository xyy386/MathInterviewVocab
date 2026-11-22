import { createPublicKey, verify } from 'node:crypto';
import { get } from 'node:https';
import { newerVersion, versionParts } from './updates';
import { windowsUpdatePublicKey } from './update-signature';

export type RouteMode = 'auto' | 'mirror' | 'direct';
export interface UpdateRoute { id: string; label: string; prefix: string }
export interface IndexAsset { name: string; bytes: number; sha256: string }
export interface DesktopIndex {
    schema: 1;
    platform: 'windows-x64';
    version: string;
    build: string;
    notes: string;
    assets: IndexAsset[];
}
export interface SignedIndex { index: DesktopIndex; bytes: Buffer; signature: Buffer }
export interface RoutedResponse {
    status: number;
    contentLength?: number;
    contentRange?: string;
    body: AsyncIterable<Uint8Array>;
    cancel(): void;
}
export type RoutedTransport = (url: string, signal: AbortSignal, offset?: number) => Promise<RoutedResponse>;

export const updateRepository = 'https://github.com/xyy386/MathInterviewVocab';
export const updateCacheLimit = 500 * 1024 * 1024;
export const updateRoutes: UpdateRoute[] = [
    { id: 'direct', label: 'GitHub 官方直连', prefix: '' },
    { id: 'ghproxy', label: '加速镜像 1 · gh-proxy', prefix: 'https://gh-proxy.com/' },
    { id: 'ghfast', label: '加速镜像 2 · ghfast', prefix: 'https://ghfast.top/' },
    { id: 'ghproxynet', label: '加速镜像 3 · ghproxy.net', prefix: 'https://ghproxy.net/' },
];
const indexPath = 'updates/windows/latest.json';
const rawIndex = `https://raw.githubusercontent.com/xyy386/MathInterviewVocab/main/${indexPath}`;
export function normalizeRouteMode(input: unknown): RouteMode {
    return input === 'direct' || input === 'mirror' ? input : 'auto';
}
export function indexCandidates(mode: RouteMode): { route: UpdateRoute; url: string }[] {
    const direct = { route: updateRoutes[0], url: rawIndex };
    if (mode === 'direct') return [direct];
    const mirrors = updateRoutes.slice(1).map(route => ({ route, url: route.prefix + rawIndex }));
    const cdns = ['testingcf.jsdelivr.net', 'fastly.jsdelivr.net'].map((host, index) => ({
        route: { id: `cdn${index}`, label: index ? 'jsDelivr Fastly' : 'jsDelivr CF', prefix: '' },
        url: `https://${host}/gh/xyy386/MathInterviewVocab@main/${indexPath}`,
    }));
    return mode === 'mirror' ? [...mirrors, ...cdns, direct] : [direct, ...mirrors, ...cdns];
}
export function assetCandidates(url: string, mode: RouteMode, preferred?: string): { route: UpdateRoute; url: string }[] {
    const routes = mode === 'direct' ? [updateRoutes[0]] : mode === 'mirror'
        ? [...updateRoutes.slice(1), updateRoutes[0]] : [...updateRoutes];
    if (preferred) routes.sort((a, b) => Number(b.id === preferred) - Number(a.id === preferred));
    return routes.map(route => ({ route, url: route.prefix + url }));
}
export function assetURL(index: DesktopIndex, name: string): string {
    if (!index.assets.some(asset => asset.name === name)) throw new Error('签名索引没有此附件。');
    return `${updateRepository}/releases/download/v${index.version}/${name}`;
}

// 原始索引先验签，再解释字段；旧版安装清单保持原格式。
export function verifyUpdateIndex(bytes: Buffer, signature: Buffer, key = windowsUpdatePublicKey): SignedIndex {
    if (bytes.length > 64 * 1024 || signature.length > 1024) throw new Error('更新索引超过允许大小。');
    const text = signature.toString('utf8').trim();
    if (!/^[A-Za-z0-9+/]{86}==$/.test(text) || !verify(null, bytes, createPublicKey(key), Buffer.from(text, 'base64'))) {
        throw new Error('更新索引数字签名无效。');
    }
    const value: unknown = JSON.parse(new TextDecoder('utf-8', { fatal: true }).decode(bytes));
    if (!value || typeof value !== 'object' || Array.isArray(value)) throw new Error('更新索引格式无效。');
    const index = value as DesktopIndex;
    if (Object.keys(index).sort().join(',') !== 'assets,build,notes,platform,schema,version'
        || index.schema !== 1 || index.platform !== 'windows-x64' || typeof index.version !== 'string'
        || typeof index.build !== 'string' || typeof index.notes !== 'string' || index.notes.length > 8000
        || !Array.isArray(index.assets) || index.assets.length !== 3) throw new Error('更新索引的平台或字段无效。');
    versionParts(index.version);
    if (!new RegExp(`^${index.version.replaceAll('.', '\\.')}\\.[1-9]\\d*$`).test(index.build)) throw new Error('更新构建号无效。');
    const expected = [`MathInterviewVocab-Windows-x64-Setup-v${index.version}.exe`, 'Windows-update.json', 'Windows-update.json.sig'];
    for (const asset of index.assets) {
        if (!asset || Object.keys(asset).sort().join(',') !== 'bytes,name,sha256'
            || !expected.includes(asset.name) || !Number.isSafeInteger(asset.bytes) || asset.bytes <= 0
            || asset.bytes > (asset.name.endsWith('.exe') ? updateCacheLimit : asset.name.endsWith('.sig') ? 1024 : 4096)
            || typeof asset.sha256 !== 'string' || !/^[a-f0-9]{64}$/.test(asset.sha256)) throw new Error('索引附件无效。');
    }
    if (new Set(index.assets.map(asset => asset.name)).size !== expected.length) throw new Error('索引附件重复。');
    return { index, bytes: Buffer.from(bytes), signature: Buffer.from(signature) };
}

export function checkedRouteURL(input: string, redirected = false): URL {
    const url = new URL(input);
    if (url.protocol !== 'https:' || url.port || url.username || url.password || url.hash
        || /%|\\/.test(url.pathname)) throw new Error('更新地址不属于允许的 HTTPS 路径。');
    const filePath = '/xyy386/MathInterviewVocab/releases/download/';
    const rawPath = '/xyy386/MathInterviewVocab/main/updates/windows/latest.json';
    const releasePath = /^\/xyy386\/MathInterviewVocab\/releases\/download\/v(0|[1-9]\d*)\.(0|[1-9]\d*)\.(0|[1-9]\d*)\/(MathInterviewVocab-Windows-x64-Setup-v[\d.]+\.exe|Windows-update\.json(?:\.sig)?)$/;
    const official = (host: string, pathname: string) => host === 'github.com' ? releasePath.test(pathname)
        : host === 'raw.githubusercontent.com' && (pathname === rawPath || pathname === rawPath + '.sig');
    let allowed = official(url.hostname, url.pathname) && !url.search;
    if (['release-assets.githubusercontent.com', 'objects.githubusercontent.com'].includes(url.hostname)) allowed = redirected;
    if (['gh-proxy.com', 'ghfast.top', 'ghproxy.net'].includes(url.hostname) && !url.search) {
        const match = /^\/https:\/\/(github\.com|raw\.githubusercontent\.com)(\/.*)$/.exec(url.pathname);
        allowed = Boolean(match && official(match[1], match[2]));
    }
    if (['testingcf.jsdelivr.net', 'fastly.jsdelivr.net'].includes(url.hostname) && !url.search) {
        allowed = url.pathname === `/gh/xyy386/MathInterviewVocab@main/${indexPath}`
            || url.pathname === `/gh/xyy386/MathInterviewVocab@main/${indexPath}.sig`;
    }
    if (!allowed || url.pathname.startsWith(filePath + '../')) throw new Error('更新地址不属于本项目的允许资源。');
    return url;
}

export const routedTransport: RoutedTransport = (input, signal, offset = 0) => {
    const request = async (url: string, redirects: number): Promise<RoutedResponse> => {
        signal.throwIfAborted();
        const target = checkedRouteURL(url, redirects > 0);
        return new Promise((resolve, reject) => {
            const call = get(target, { signal, headers: {
                'User-Agent': 'MathInterviewVocab-Updater', 'Accept-Encoding': 'identity', 'Cache-Control': 'no-cache',
                ...(offset ? { Range: `bytes=${offset}-` } : {}),
            } }, response => {
                clearTimeout(firstByte);
                const status = response.statusCode ?? 0;
                if ([301, 302, 303, 307, 308].includes(status)) {
                    response.destroy();
                    try {
                        if (redirects >= 5 || !response.headers.location) throw new Error('更新重定向无效或次数过多。');
                        resolve(request(new URL(response.headers.location, target).toString(), redirects + 1));
                    } catch (error) { reject(error); }
                    return;
                }
                if (![200, 206, 416].includes(status)) { response.destroy(); reject(new Error(`更新线路返回 HTTP ${status}，可切换线路重试。`)); return; }
                const length = response.headers['content-length'];
                if ((length && !/^\d+$/.test(length)) || (response.headers['content-encoding'] && response.headers['content-encoding'] !== 'identity')) {
                    response.destroy(); reject(new Error('更新响应长度或编码无效。')); return;
                }
                response.setTimeout(12_000, () => response.destroy(new Error('更新线路读取超时。')));
                resolve({ status, contentLength: length ? Number(length) : undefined,
                    contentRange: response.headers['content-range'], body: response, cancel: () => response.destroy() });
            });
            const firstByte = setTimeout(() => call.destroy(new Error('更新线路首包超时。')), 6000);
            call.once('error', error => { clearTimeout(firstByte); reject(error); });
        });
    };
    return request(input, 0);
};

export function responseRange(response: Pick<RoutedResponse, 'status' | 'contentLength' | 'contentRange'>, offset: number, total: number): number {
    if (response.status === 200) {
        if (response.contentLength !== undefined && response.contentLength !== total) throw new Error('安装包大小与签名索引不一致。');
        return 0;
    }
    const match = /^bytes (\d+)-(\d+)\/(\d+)$/.exec(response.contentRange ?? '');
    if (response.status !== 206 || !match || Number(match[1]) !== offset || Number(match[2]) !== total - 1
        || Number(match[3]) !== total || (response.contentLength !== undefined && response.contentLength !== total - offset)) {
        throw new Error('断点续传响应范围无效。');
    }
    return offset;
}

export async function smallResponse(response: RoutedResponse, signal: AbortSignal, maximum: number, expected?: number): Promise<Buffer> {
    try {
        if (response.status !== 200 || (response.contentLength !== undefined && (response.contentLength > maximum
            || (expected !== undefined && response.contentLength !== expected)))) throw new Error('更新元数据响应大小无效。');
        const chunks: Buffer[] = []; let bytes = 0;
        for await (const chunk of response.body) {
            signal.throwIfAborted(); bytes += chunk.byteLength;
            if (bytes > maximum) throw new Error('更新元数据超过允许大小。');
            chunks.push(Buffer.from(chunk));
        }
        signal.throwIfAborted();
        if (expected !== undefined && bytes !== expected) throw new Error('更新元数据不完整。');
        return Buffer.concat(chunks);
    } finally { response.cancel(); }
}

/** 小元数据最多三路对冲；无更新结果为较慢的新鲜版本预留短窗口。 */
export async function raceIndices(
    mode: RouteMode, signal: AbortSignal, transport: RoutedTransport, publicKey?: string,
    installed = '0.0.0', knownVersion = installed, hedgeDelay = mode === 'mirror' ? 400 : 1200,
): Promise<{ signed: SignedIndex; route: UpdateRoute }> {
    const candidates = indexCandidates(mode);
    const controllers = candidates.map(() => new AbortController());
    let next = 0; let active = 0; let completed = 0; let settled = false;
    let fallback: { signed: SignedIndex; route: UpdateRoute } | undefined;
    let failure: unknown;
    return new Promise((resolve, reject) => {
        let hedge: ReturnType<typeof setTimeout> | undefined;
        let grace: ReturnType<typeof setTimeout> | undefined;
        const finish = (result?: typeof fallback, error?: unknown) => {
            if (settled) return; settled = true;
            clearTimeout(hedge); clearTimeout(grace); signal.removeEventListener('abort', abort);
            controllers.forEach(controller => controller.abort());
            result ? resolve(result) : reject(error ?? failure ?? new Error('所有更新线路均不可用。'));
        };
        const abort = () => finish(undefined, signal.reason);
        const launch = () => {
            if (settled || next >= candidates.length || active >= 3) return;
            const index = next++; active += 1;
            const { route, url } = candidates[index];
            const combined = AbortSignal.any([signal, controllers[index].signal]);
            void (async () => {
                const [bytes, signature] = await Promise.all([
                    transport(url, combined).then(response => smallResponse(response, combined, 64 * 1024)),
                    transport(url + '.sig', combined).then(response => smallResponse(response, combined, 1024)),
                ]);
                const signed = verifyUpdateIndex(bytes, signature, publicKey);
                if (newerVersion(knownVersion, signed.index.version)) throw new Error('线路返回旧版缓存，未覆盖已知版本。');
                return { signed, route };
            })().then(result => {
                if (settled) return;
                if (newerVersion(result.signed.index.version, installed) || route.id === 'direct') finish(result);
                else {
                    if (!fallback || newerVersion(result.signed.index.version, fallback.signed.index.version)) fallback = result;
                    grace ??= setTimeout(() => finish(fallback), 800);
                }
            }).catch(error => { failure = error; }).finally(() => {
                controllers[index].abort(); active -= 1; completed += 1;
                if (settled) return;
                if (completed === candidates.length) finish(fallback);
                else { launch(); if (next > 1) { launch(); launch(); } }
            });
        };
        signal.addEventListener('abort', abort, { once: true });
        if (signal.aborted) { abort(); return; }
        launch();
        hedge = setTimeout(() => { launch(); launch(); }, hedgeDelay);
    });
}
