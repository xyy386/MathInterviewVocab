// 渲染器事件夹具，不执行安装包，也不冒充 Windows 原生安装验收。
import { app, BrowserWindow, ipcMain, session } from 'electron';
import assert from 'node:assert/strict';
import { mkdtempSync, readFileSync, rmSync, writeFileSync } from 'node:fs';
import { tmpdir } from 'node:os';
import path from 'node:path';
import { fileURLToPath } from 'node:url';

const root = path.resolve(path.dirname(fileURLToPath(import.meta.url)), '..');
const directory = mkdtempSync(path.join(process.env.MATH_VOCAB_VALIDATION_DIRECTORY ?? tmpdir(), 'vocab-update-ui-'));
app.setPath('userData', directory);
app.disableHardwareAcceleration();
app.commandLine.appendSwitch('disable-background-networking');
let window;
let mainWindow;
let transferScenario = false;
let state = { currentVersion: '1.5.0', status: 'idle', message: '手动检查', canDownload: false, routeMode: 'auto' };
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
    for (const current of [window, mainWindow]) if (current && !current.isDestroyed()) current.webContents.send('vocab:update-changed', state);
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
        calls.push('check'); return publish({ status: 'available', canDownload: true, version: '1.5.1', notes: 'Fixture notes', message: '发现新版' });
    });
    ipcMain.handle('vocab:update-download', () => {
        calls.push('download');
        return publish(transferScenario && state.status !== 'paused'
            ? { status: 'downloading', canDownload: false, hasCachedDownload: true, canResume: false, downloadedBytes: 1048576, totalBytes: 4194304,
                speedBytesPerSecond: 235520, activeRouteLabel: '加速镜像 1 · gh-proxy', message: '正在断点续传' }
            : { status: 'ready', canResume: false, canDownload: false, hasCachedDownload: true, downloadedBytes: 4194304, totalBytes: 4194304, message: '签名通过，请确认安装' });
    });
    ipcMain.handle('vocab:update-install', () => {
        calls.push('install'); return publish({ status: 'installing', canDownload: false, message: '安装器交接中' });
    });
    ipcMain.handle('vocab:update-pause', () => { calls.push('pause'); return publish({ status: 'paused', canResume: true, canDownload: true, speedBytesPerSecond: 0, message: '断点已保留' }); });
    ipcMain.handle('vocab:update-route', (_event, mode) => { calls.push(`route:${mode}`); return publish({ routeMode: mode }); });
    ipcMain.handle('vocab:update-clear', () => { calls.push('clear'); return publish({ status: 'idle', hasCachedDownload: false, canResume: false, message: '已清除测试缓存' }); });
    ipcMain.handle('vocab:open-settings', () => { calls.push('view'); });
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
    transferScenario = true;
    await click('加速镜像优先');
    await waitFor('Array.from(document.querySelectorAll("button")).find(button => button.textContent === "加速镜像优先").getAttribute("aria-pressed") === "true"');
    await click('下载更新');
    await waitFor('document.querySelector(".update-transfer-detail").textContent.includes("230 KB/s")');
    assert.equal(await evaluate('Array.from(document.querySelectorAll("button")).find(button => button.textContent === "GitHub 直连").disabled'), true);
    const settings = window;
    mainWindow = new BrowserWindow({ show: false, width: 1220, height: 780,
        webPreferences: { preload: path.join(root, '.out/app/preload.cjs'), contextIsolation: true, sandbox: true } });
    await mainWindow.loadFile(path.join(root, '.out/app/ui/index.html'));
    window = mainWindow;
    await waitFor('Boolean(document.querySelector("[data-testid=update-banner]"))');
    settings.destroy();
    assert.equal(state.status, 'downloading', 'closing settings must not cancel a transfer');
    for (const tab of ['检测', '学习记录', '词汇']) {
        await click(tab);
        assert.equal(await evaluate('document.querySelector("[data-testid=update-banner]").textContent.includes("230 KB/s")'), true);
    }
    for (const width of [1000, 1220, 1440]) {
        mainWindow.setSize(width, 780);
        for (let sidebar = 0; sidebar < 2; sidebar += 1) {
            await evaluate('document.querySelector(".sidebar-toggle").click()');
            await evaluate('new Promise(resolve => requestAnimationFrame(() => requestAnimationFrame(resolve)))');
            await pause();
            const fit = await evaluate(`(() => { const r = document.querySelector('[data-testid=update-banner]').getBoundingClientRect();
                const actions = document.querySelector('.update-banner-actions').getBoundingClientRect();
                return r.left >= 0 && r.right <= innerWidth && actions.right <= innerWidth && r.bottom <= document.querySelector('.main-content').getBoundingClientRect().top + 1; })()`);
            assert.equal(fit, true, `banner layout ${width} sidebar ${sidebar}`);
            if (process.env.MATH_VOCAB_VALIDATION_DIRECTORY) {
                const snapshot = await mainWindow.webContents.capturePage();
                writeFileSync(path.join(process.env.MATH_VOCAB_VALIDATION_DIRECTORY, `windows-update-${width}-${sidebar}.png`), snapshot.toPNG());
            }
        }
    }
    await click('暂停'); await waitFor('document.querySelector("[data-testid=update-banner]").textContent.includes("已暂停")');
    assert.equal(state.downloadedBytes, 1048576);
    await click('继续'); await waitFor('document.querySelector("[data-testid=update-banner]").textContent.includes("已就绪")');
    await click('查看'); assert.ok(calls.includes('view'));
    console.log(`PASS background renderer: route controls, speed, settings close, three modules, 1000/1220/1440 widths, both sidebar states, pause/resume (${process.platform}, no package executed)`);
} catch (error) { console.error(error); exitCode = 1; }
finally {
    window?.destroy();
    if (mainWindow && !mainWindow.isDestroyed()) mainWindow.destroy();
    await session.defaultSession.clearStorageData();
    rmSync(directory, { recursive: true, force: true });
    app.exit(exitCode);
}
}
app.whenReady().then(run).catch(error => { console.error(error); app.exit(1); });
