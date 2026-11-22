// 独立验收服务：固定只读资源、可控节流和 Range，不读取用户数据或公开到局域网。
import { createServer } from 'node:http';
import { createReadStream } from 'node:fs';
import { appendFile, readFile, stat } from 'node:fs/promises';
import { once } from 'node:events';
import path from 'node:path';

const [root, portText = '18772'] = process.argv.slice(2);
if (!root || !path.isAbsolute(root) || !root.includes('/Documents/codextest/') || !/^\d{4,5}$/.test(portText)
    || Number(portText) < 1024 || Number(portText) > 65535) throw new Error('Use an explicit codextest fixture root and local port.');
const fixture = JSON.parse(await readFile(path.join(root, 'fixture.json'), 'utf8'));
const serverRoot = path.join(root, 'server');
const log = path.join(root, 'server-access.jsonl');
const prefix = `/releases/download/v${fixture.version}/`;
const resources = new Set(['/updates/macos/latest.json', '/updates/macos/latest.json.sig', `${prefix}appcast.xml`, `${prefix}${fixture.archive}`]);
const server = createServer(async (request, response) => {
    let stream;
    try {
        const url = request.url ?? '';
        if (!['GET', 'HEAD'].includes(request.method) || !resources.has(url)) { response.writeHead(404); response.end(); return; }
        const mode = JSON.parse(await readFile(path.join(serverRoot, 'mode.json'), 'utf8'));
        let file = path.join(serverRoot, url);
        if (mode.badFeed && !url.endsWith('.zip')) file = path.join(serverRoot, 'negative', path.basename(url));
        const bytes = (await stat(file)).size;
        const range = request.headers.range;
        const match = typeof range === 'string' ? /^bytes=(\d+)-$/.exec(range) : null;
        const offset = match ? Number(match[1]) : 0;
        await appendFile(log, JSON.stringify({ at: new Date().toISOString(), method: request.method, path: url, offset, badFeed: mode.badFeed }) + '\n');
        if ((range && !match) || offset >= bytes) { response.writeHead(416, { 'Content-Range': `bytes */${bytes}` }); response.end(); return; }
        response.writeHead(offset ? 206 : 200, { 'Content-Type': url.endsWith('.zip') ? 'application/octet-stream' : 'application/json',
            'Content-Length': bytes - offset, 'Cache-Control': 'no-store', 'Accept-Ranges': 'bytes',
            ...(offset ? { 'Content-Range': `bytes ${offset}-${bytes - 1}/${bytes}` } : {}) });
        if (request.method === 'HEAD') { response.end(); return; }
        stream = createReadStream(file, { start: offset, highWaterMark: 64 * 1024 });
        response.once('close', () => stream.destroy());
        for await (const chunk of stream) {
            if (response.destroyed) break;
            if (!response.write(chunk)) await once(response, 'drain');
            if (url.endsWith('.zip') && mode.chunkDelayMs > 0) await new Promise(resolve => setTimeout(resolve, mode.chunkDelayMs));
        }
        response.end();
    } catch { stream?.destroy(); response.destroy(); }
});
server.listen(Number(portText), '127.0.0.1', () => console.log(`Read-only update fixture listening on 127.0.0.1:${portText}`));
