// 渲染器事件夹具，不执行安装包，也不冒充 Windows 原生安装验收。
import { app, BrowserWindow, ipcMain, session } from 'electron';
import assert from 'node:assert/strict';
import { mkdtempSync, readFileSync, rmSync } from 'node:fs';
import { tmpdir } from 'node:os';
import path from 'node:path';
import { fileURLToPath } from 'node:url';

const root = path.resolve(path.dirname(fileURLToPath(import.meta.url)), '..');
const directory = mkdtempSync(path.join(process.env.MATH_VOCAB_VALIDATION_DIRECTORY ?? tmpdir(), 'vocab-update-ui-'));
app.setPath('userData', directory);
app.commandLine.appendSwitch('disable-background-networking');
let window;
let state = { currentVersion: '1.4.0', status: 'idle', message: '手动检查', canDownload: false };
const calls = [];
const evaluate = source => window.webContents.executeJavaScript(source);
const pause = () => new Promise(resolve => setTimeout(resolve, 30));
async function waitFor(source) {
    for (let count = 0; count < 100; count += 1) {
        if (await evaluate(source)) return;
        await pause();
    }
    throw new Error(`Update UI fixture timed out: ${source}`);
}
async function click(text) {
    await evaluate(`Array.from(document.querySelectorAll('button')).find(button => button.textContent === ${JSON.stringify(text)})?.click()`);
    await pause();
}
function publish(patch) {
    state = { ...state, ...patch };
    window.webContents.send('vocab:update-changed', state);
    return state;
}

async function run() {
let exitCode = 0;
try {
    session.defaultSession.webRequest.onBeforeRequest({ urls: ['http://*/*', 'https://*/*'] }, (_request, done) => done({ cancel: true }));
    ipcMain.handle('vocab:load', () => ({
        terms: JSON.parse(readFileSync(path.join(root, '../Resources/terms.json'), 'utf8')),
        progress: { attempts: [], favoriteIDs: [], wrongIDs: [], masteredIDs: [], correctStreak: {},
            quizPreferences: { direction: 'mixed', scope: 'important', questionLength: 'ten', selectedSubjectID: 'analysis', includeMastered: true } },
        settings: { accent: 'en-US', speed: 1 }, storagePath: directory,
        audioInfo: { source: 'Kokoro', clips: 2328, texts: 1164 },
    }));
    ipcMain.handle('vocab:update-state', () => state);
    ipcMain.handle('vocab:update-check', () => {
        calls.push('check'); return publish({ status: 'available', canDownload: true, version: '1.4.1', notes: 'Fixture notes', message: '发现新版' });
    });
    ipcMain.handle('vocab:update-download', () => {
        calls.push('download'); return publish({ status: 'ready', canDownload: false, message: '签名通过，请确认安装' });
    });
    ipcMain.handle('vocab:update-install', () => {
        calls.push('install'); return publish({ status: 'installing', canDownload: false, message: '安装器交接中' });
    });
    window = new BrowserWindow({ show: false, width: 480, height: 680,
        webPreferences: { preload: path.join(root, '.out/app/preload.cjs'), contextIsolation: true, sandbox: true } });
    await window.loadFile(path.join(root, '.out/app/ui/index.html'), { query: { window: 'settings' } });
    await waitFor('Boolean(document.querySelector("[data-testid=software-update]"))');
    assert.deepEqual(calls, [], 'opening settings must not check or download automatically');
    await click('检查更新');
    await waitFor('Array.from(document.querySelectorAll("button")).some(button => button.textContent === "下载更新")');
    assert.deepEqual(calls, ['check']);
    await click('下载更新');
    await waitFor('Array.from(document.querySelectorAll("button")).some(button => button.textContent === "安装并重启")');
    assert.deepEqual(calls, ['check', 'download'], 'download must not execute installer');
    await click('安装并重启');
    await waitFor('document.querySelector(".update-status").textContent.includes("交接中")');
    assert.deepEqual(calls, ['check', 'download', 'install']);
    assert.equal(await evaluate('Array.from(document.querySelectorAll("button")).find(button => button.textContent === "检查更新").disabled'), true);
    publish({ status: 'error', canDownload: true, message: '安装器启动失败，应用尚未退出。' });
    await waitFor('document.querySelector(".update-status").getAttribute("role") === "alert"');
    assert.equal(await evaluate('Array.from(document.querySelectorAll("button")).find(button => button.textContent === "下载更新").disabled'), false);
    console.log(`PASS update renderer: manual check → signed download → explicit install → busy → visible retry (${process.platform}, hidden fixture, no package executed)`);
} catch (error) { console.error(error); exitCode = 1; }
finally {
    window?.destroy();
    await session.defaultSession.clearStorageData();
    rmSync(directory, { recursive: true, force: true });
    app.exit(exitCode);
}
}
app.whenReady().then(run).catch(error => { console.error(error); app.exit(1); });
