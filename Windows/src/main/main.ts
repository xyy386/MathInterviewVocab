import { app, BrowserWindow, clipboard, dialog, ipcMain, Menu, net, protocol, session, shell, type IpcMainInvokeEvent } from 'electron';
import path from 'node:path';
import { mkdirSync, readFileSync } from 'node:fs';
import { pathToFileURL } from 'node:url';
import { LocalRepository } from './persistence';
import { BundledAudio } from './audio';
import { sanitizeSelection, type Term } from '../shared/core';
import { AcceleratedUpdater } from './accelerated-updates';
import { launchInstaller } from './installer';

app.setName('MathInterviewVocab');
app.setAppUserModelId('com.xingyiyao.MathInterviewVocab');
protocol.registerSchemesAsPrivileged([{ scheme: 'vocab', privileges: {
    standard: true, secure: true, supportFetchAPI: true, stream: true, corsEnabled: true,
} }]);

const root = __dirname;
const resources = path.join(root, 'resources');
let mainWindow: BrowserWindow | null = null;
let settingsWindow: BrowserWindow | null = null;

function dataDirectory(): string {
    if (!app.isPackaged && process.env.MATH_VOCAB_TEST_DATA_DIR) return path.resolve(process.env.MATH_VOCAB_TEST_DATA_DIR);
    if (process.platform === 'win32') {
        const local = process.env.LOCALAPPDATA ?? path.join(app.getPath('home'), 'AppData', 'Local');
        return path.join(local, 'MathInterviewVocab');
    }
    // 开发与验收始终隔离，不写入原生 Mac 应用的目录。
    return path.join(app.getPath('appData'), 'MathInterviewVocab-Windows-Preview');
}

const directory = dataDirectory();
let startupPathError: unknown = null;
try {
    const sessionDirectory = path.join(directory, 'chromium');
    mkdirSync(sessionDirectory, { recursive: true });
    app.setPath('userData', directory);
    app.setPath('sessionData', sessionDirectory);
} catch (error) {
    startupPathError = error;
}
app.commandLine.appendSwitch('disable-background-networking');

async function initialize(): Promise<void> {
    const terms = JSON.parse(readFileSync(path.join(resources, 'terms.json'), 'utf8')) as Term[];
    if (terms.length !== 326 || terms.filter(t => t.important).length !== 130 || new Set(terms.map(t => t.id)).size !== 326) {
        throw new Error('内置词库校验失败，请重新解压完整软件包。');
    }
    const repository = new LocalRepository(directory, new Set(terms.map(t => t.id)));
    const audio = new BundledAudio(resources);

    // 学习与发音页面仍禁止 HTTP(S)；手动更新仅由独立主进程请求。
    session.defaultSession.webRequest.onBeforeRequest({ urls: ['http://*/*', 'https://*/*'] }, (_details, callback) => callback({ cancel: true }));
    session.defaultSession.setPermissionRequestHandler((_contents, _permission, callback) => callback(false));
    protocol.handle('vocab', async request => {
        const url = new URL(request.url);
        if (url.hostname !== 'bundle') return new Response('Not found', { status: 404 });
        const relative = decodeURIComponent(url.pathname).replace(/^\/+/, '');
        const isAudio = /^audio\/[a-f0-9]{64}\.mp3$/.test(relative);
        const base = isAudio ? resources : path.join(root, 'ui');
        const target = path.resolve(base, relative || 'index.html');
        if (!target.startsWith(path.resolve(base) + path.sep)) return new Response('Forbidden', { status: 403 });
        return net.fetch(pathToFileURL(target).toString());
    });

    const windows = () => [mainWindow, settingsWindow].filter((window): window is BrowserWindow => Boolean(window && !window.isDestroyed()));
    const broadcast = (change: unknown) => windows().forEach(window => window.webContents.send('vocab:data-changed', change));
    let installing = false;
    let confirmingInstall = false;
    const updater = new AcceleratedUpdater(app.getVersion(), path.join(directory, 'UpdatesCache'),
        state => windows().forEach(window => window.webContents.send('vocab:update-changed', state)),
        file => shell.showItemInFolder(file), url => shell.openExternal(url), {
            launch: async file => {
                if (repository.error) throw new Error('学习记录或设置存在保存／读取错误，请先处理后再安装更新。原文件会保留。');
                if (!app.isPackaged || process.platform !== 'win32') throw new Error('安装更新仅支持 Windows 正式应用。');
                installing = true;
                windows().forEach(window => window.webContents.send('vocab:stop-audio'));
                try { await launchInstaller(file, { directory, quit: () => app.quit() }); }
                catch (error) { installing = false; throw error; }
            },
        });
    await updater.restore();
    let waitingToQuit = false;
    app.on('before-quit', event => {
        if (!installing && updater.busy) {
            event.preventDefault();
            if (!waitingToQuit) {
                waitingToQuit = true;
                void updater.pause().finally(() => app.quit());
            }
        }
    });
    const protectWindow = (window: BrowserWindow) => {
        window.webContents.setWindowOpenHandler(() => ({ action: 'deny' }));
        window.webContents.on('will-navigate', (event, url) => {
            if (!url.startsWith('vocab://bundle/index.html')) event.preventDefault();
        });
    };
    const windowOptions = {
        frame: false, backgroundColor: '#F7F9FC', autoHideMenuBar: true,
        icon: path.join(resources, 'icon.png'), show: false,
        webPreferences: { preload: path.join(root, 'preload.cjs'), nodeIntegration: false, contextIsolation: true,
            sandbox: true, webSecurity: true, spellcheck: false },
    };
    const openSettings = async () => {
        if (settingsWindow && !settingsWindow.isDestroyed()) {
            if (settingsWindow.isMinimized()) settingsWindow.restore();
            settingsWindow.show(); settingsWindow.focus(); return;
        }
        settingsWindow = new BrowserWindow({ ...windowOptions, title: '“数学面试词汇”设置',
            width: 480, height: 680, resizable: false, maximizable: false, fullscreenable: false });
        const window = settingsWindow;
        protectWindow(window);
        window.once('ready-to-show', () => { window.show(); window.focus(); });
        window.on('closed', () => {
            settingsWindow = null;
            windows().forEach(window => window.webContents.send('vocab:stop-audio'));
        });
        await window.loadURL('vocab://bundle/index.html?window=settings');
    };
    const handle = (channel: string, callback: (event: IpcMainInvokeEvent, ...args: unknown[]) => unknown) => {
        ipcMain.handle(channel, (event, ...args) => {
            if (!windows().some(window => window.webContents === event.sender)
                || event.senderFrame !== event.sender.mainFrame
                || !event.senderFrame?.url.startsWith('vocab://bundle/index.html')) {
                throw new Error('不允许的窗口请求。');
            }
            if (installing && ['vocab:favorite', 'vocab:preferences', 'vocab:answer', 'vocab:reset', 'vocab:settings'].includes(channel)) {
                throw new Error('正在安装更新，请等待应用重新启动。');
            }
            return callback(event, ...args);
        });
    };
    handle('vocab:load', () => ({ terms, progress: repository.progress, settings: repository.settings,
        storagePath: repository.directory, audioInfo: audio.info, persistenceError: repository.error }));
    handle('vocab:favorite', (_event, id) => {
        if (!Number.isInteger(id)) throw new Error('无效词汇编号。');
        const progress = repository.favorite(id as number); broadcast({ progress }); return progress;
    });
    handle('vocab:preferences', (_event, preferences) => {
        const progress = repository.preferences(preferences); broadcast({ progress }); return progress;
    });
    handle('vocab:answer', (_event, attempt) => {
        const progress = repository.answer(attempt); broadcast({ progress }); return progress;
    });
    handle('vocab:reset', () => {
        const progress = repository.reset(); broadcast({ progress, persistenceError: repository.error }); return progress;
    });
    handle('vocab:settings', (_event, settings) => {
        const value = repository.setSettings(settings); broadcast({ settings: value }); return value;
    });
    handle('vocab:audio', (event, text, accent) => {
        windows().filter(window => window.webContents !== event.sender).forEach(window => window.webContents.send('vocab:stop-audio'));
        return audio.find(text, accent);
    });
    handle('vocab:open-settings', () => openSettings());
    handle('vocab:update-state', () => updater.state);
    handle('vocab:update-check', () => updater.check());
    let confirmingDownload = false;
    handle('vocab:update-download', async event => {
        if (!updater.needsCacheReplacement) return updater.download();
        if (confirmingDownload || updater.busy) return updater.state;
        const window = BrowserWindow.fromWebContents(event.sender);
        if (!window) throw new Error('无法找到更新窗口。');
        confirmingDownload = true;
        const version = updater.state.version;
        try {
            const result = await dialog.showMessageBox(window, { type: 'question', title: '替换旧的下载断点？',
                message: `开始下载 ${version ?? ''}，并清除旧版更新缓存？`,
                detail: '仅清除本软件的更新下载，不影响学习记录、收藏或设置。', buttons: ['保留旧断点', '下载新版'],
                defaultId: 0, cancelId: 0, noLink: true });
            if (result.response !== 1) return updater.state;
            if (updater.state.version !== version) throw new Error('候选版本已改变，请重新确认下载。');
            return updater.download(true);
        } finally { confirmingDownload = false; }
    });
    handle('vocab:update-install', async event => {
        if (confirmingInstall || installing || updater.state.status !== 'ready') return updater.state;
        const window = BrowserWindow.fromWebContents(event.sender);
        if (!window) throw new Error('无法找到更新窗口。');
        confirmingInstall = true;
        try {
            const result = await dialog.showMessageBox(window, { type: 'question', title: '安装更新并重启？',
                message: `安装版本 ${updater.state.version ?? ''} 并重新启动？`,
                detail: '应用将正常退出，由安装器替换程序文件后重新启动。答题记录、收藏和发音设置会保留。系统安全提示仍可能出现。',
                buttons: ['取消', '安装并重启'], defaultId: 0, cancelId: 0, noLink: true });
            return result.response === 1 ? await updater.install() : updater.state;
        } finally { confirmingInstall = false; }
    });
    handle('vocab:update-cancel', () => updater.pause());
    handle('vocab:update-pause', () => updater.pause());
    handle('vocab:update-route', (_event, mode) => {
        if (!['auto', 'mirror', 'direct'].includes(String(mode))) throw new Error('无效更新线路。');
        return updater.setRouteMode(mode);
    });
    handle('vocab:update-clear', async event => {
        if (updater.busy) return updater.state;
        const window = BrowserWindow.fromWebContents(event.sender);
        if (!window) throw new Error('无法找到更新窗口。');
        const result = await dialog.showMessageBox(window, { type: 'question', title: '清除更新下载缓存？',
            message: '清除已保留的更新安装包和下载断点？', detail: '不会删除学习记录、收藏、错题、发音设置或更新线路偏好。',
            buttons: ['保留缓存', '清除下载缓存'], defaultId: 0, cancelId: 0, noLink: true });
        return result.response === 1 ? updater.clearCache() : updater.state;
    });
    handle('vocab:update-reveal', () => updater.showDownload());
    handle('vocab:update-release', () => updater.showRelease());
    handle('vocab:window-action', (event, action) => {
        const window = BrowserWindow.fromWebContents(event.sender);
        if (action === 'close') window?.close();
        else if (action === 'minimize') window?.minimize();
        else if (action === 'maximize' && window && window === mainWindow) {
            if (window.isMaximized()) window.unmaximize(); else window.maximize();
        } else if (action !== 'maximize') throw new Error('无效窗口操作。');
    });
    handle('vocab:confirm-reset', async event => {
        const window = BrowserWindow.fromWebContents(event.sender);
        if (!window) return false;
        const result = await dialog.showMessageBox(window, { type: 'warning', title: '确定清空学习记录？',
            message: '确定清空学习记录？', detail: '答题历史、收藏、错题和已掌握记录将被清空。检测偏好与发音设置不受影响。',
            buttons: ['取消', '清空记录'], defaultId: 0, cancelId: 0, noLink: true });
        return result.response === 1;
    });
    handle('vocab:selection-menu', (event, input) => {
        if (typeof input !== 'string' || input.length > 600) return;
        const selected = sanitizeSelection(input);
        Menu.buildFromTemplate([
            { label: '复制', enabled: Boolean(input), click: () => clipboard.writeText(input) },
            { type: 'separator' },
            { label: '朗读所选', enabled: Boolean(selected), click: () => {
                if (selected && !event.sender.isDestroyed()) event.sender.send('vocab:speak-selection', selected);
            } },
        ]).popup({ window: BrowserWindow.fromWebContents(event.sender) ?? undefined });
    });

    Menu.setApplicationMenu(null);
    // 仅开发验收可指定初始尺寸；最终成品始终采用正式窗口配置。
    const testSize = !app.isPackaged ? process.env.MATH_VOCAB_TEST_WINDOW_SIZE?.match(/^(1000|1220|1440)x(680|780|900)$/) : null;
    mainWindow = new BrowserWindow({
        title: '数学面试词汇', width: testSize ? Number(testSize[1]) : 1220,
        height: testSize ? Number(testSize[2]) : 780, minWidth: 1000, minHeight: 680,
        ...windowOptions,
    });
    protectWindow(mainWindow);
    mainWindow.once('ready-to-show', () => mainWindow?.show());
    mainWindow.on('closed', () => { mainWindow = null; settingsWindow?.close(); });
    await mainWindow.loadURL('vocab://bundle/index.html');
}

if (startupPathError) {
    app.whenReady().then(() => {
        dialog.showErrorBox('无法创建学习记录目录', `请检查本机用户目录的可写权限。\n${String(startupPathError)}`);
        app.quit();
    });
} else if (!app.requestSingleInstanceLock()) app.quit();
else {
    app.on('second-instance', () => { if (mainWindow?.isMinimized()) mainWindow.restore(); mainWindow?.focus(); });
    app.whenReady().then(initialize).catch(error => {
        dialog.showErrorBox('数学面试词汇无法启动', `${String(error)}\n请完整解压 ZIP 后再运行 EXE。`);
        app.quit();
    });
    app.on('window-all-closed', () => app.quit());
}
