import { createRequire } from 'node:module';
import { fileURLToPath } from 'node:url';
import { createHash } from 'node:crypto';
import { readFile, writeFile, mkdtemp, rm } from 'node:fs/promises';
import { mkdtempSync } from 'node:fs';
import path from 'node:path';

const require = createRequire(path.resolve(path.dirname(fileURLToPath(import.meta.url)), '../Windows/package.json'));
const { app, BrowserWindow, session } = require('electron');

const directory = process.argv[2];
const validation = process.env.MATH_VOCAB_VALIDATION_DIRECTORY;
if (!directory || !validation?.startsWith('/') || !validation.includes('/Documents/codextest/')) throw new Error('An explicit codextest validation directory is required.');
const userData = mkdtempSync(path.join(validation, 'math-pdf-'));
app.setPath('userData', userData); app.disableHardwareAcceleration();
async function run() {
session.defaultSession.webRequest.onBeforeRequest({ urls: ['http://*/*', 'https://*/*'] }, (_details, done) => done({ cancel: true }));
const window = new BrowserWindow({ show: false, webPreferences: { sandbox: true, contextIsolation: true } });
try {
    const manifest = JSON.parse(await readFile(path.join(directory, 'manifest.json'), 'utf8'));
    for (const entry of manifest.entries) {
        const width = entry.widthEm * 100, height = entry.heightEm * 100;
        const svg = (await readFile(path.join(directory, entry.svg), 'utf8'))
            .replace(/width="[^"]+"/, `width="${width}pt"`).replace(/height="[^"]+"/, `height="${height}pt"`);
        const html = `<!doctype html><html><head><meta http-equiv="Content-Security-Policy" content="default-src 'none'; style-src 'unsafe-inline'"><style>@page{size:${width}pt ${height}pt;margin:0}html,body{margin:0;padding:0;width:${width}pt;height:${height}pt;color:#000}svg{display:block}</style></head><body>${svg}</body></html>`;
        await window.loadURL('data:text/html;charset=utf-8,' + encodeURIComponent(html));
        const pdf = await window.webContents.printToPDF({ printBackground: false, preferCSSPageSize: true,
            margins: { top: 0, bottom: 0, left: 0, right: 0 }, pageSize: { width: width / 72, height: height / 72 } });
        await writeFile(path.join(directory, entry.pdf), pdf);
        entry.pdfSHA256 = createHash('sha256').update(pdf).digest('hex');
    }
    await writeFile(path.join(directory, 'manifest.json'), JSON.stringify(manifest, null, 2) + '\n');
} finally { window.destroy(); await rm(userData, { recursive: true, force: true }); app.quit(); }
}
app.whenReady().then(run).catch(error => { console.error(error); app.exit(1); });
