// 实际渲染、选区、复制和键盘事件；不访问用户数据或系统剪贴板。
import { app, BrowserWindow, ipcMain, session } from 'electron';
import assert from 'node:assert/strict';
import { mkdtempSync, readFileSync, rmSync, writeFileSync } from 'node:fs';
import path from 'node:path';
import { fileURLToPath } from 'node:url';

const root = path.resolve(path.dirname(fileURLToPath(import.meta.url)), '..');
const output = process.env.MATH_VOCAB_VALIDATION_DIRECTORY;
if (!output?.startsWith('/') || !output.includes('/Documents/codextest/')) throw new Error('An explicit validation directory is required.');
const directory = mkdtempSync(path.join(output, 'math-render-ui-'));
app.setPath('userData', directory); app.disableHardwareAcceleration();
let window; let blocked = 0; const audioRequests = [];
const terms = JSON.parse(readFileSync(path.join(root, '../Resources/terms.json'), 'utf8'));
const progress = { attempts: [], favoriteIDs: [323, 324, 325, 326], wrongIDs: [323], masteredIDs: [], correctStreak: {},
    quizPreferences: { direction: 'zhToEn', scope: 'favorites', questionLength: 'all', selectedSubjectID: 'statistics', includeMastered: true } };
const evaluate = text => window.webContents.executeJavaScript(text);
const pause = () => new Promise(resolve => setTimeout(resolve, 35));
async function waitFor(text) {
    for (let n = 0; n < 100; n += 1) { if (await evaluate(text)) return; await pause(); }
    throw new Error('Math renderer fixture timed out: ' + text);
}
async function open() {
    await window.loadFile(path.join(root, '.out/app/ui/index.html'));
    await waitFor('Boolean(document.querySelector("[data-testid=glossary-welcome]"))');
    await evaluate(`Object.defineProperty(document, 'hasFocus', { configurable: true, value: () => true });
        Object.defineProperty(document, 'visibilityState', { configurable: true, get: () => 'visible' }); void 0;`);
}
async function term(id) {
    await evaluate(`document.querySelector('[data-term-id="${id}"]').click()`);
    await waitFor(`document.querySelector('.term-reference')?.textContent === '#${id}'`);
}
async function selectAndCopy(selector) {
    return evaluate(`(() => { const range=document.createRange();range.selectNodeContents(document.querySelector(${JSON.stringify(selector)}));
        const selection=window.getSelection();selection.removeAllRanges();selection.addRange(range);
        const data=new DataTransfer();document.dispatchEvent(new ClipboardEvent('copy',{bubbles:true,cancelable:true,clipboardData:data}));return data.getData('text/plain'); })()`);
}
async function key(value) {
    return evaluate(`(() => { const e=new KeyboardEvent('keydown',{key:${JSON.stringify(value)},bubbles:true,cancelable:true});document.body.dispatchEvent(e);return e.defaultPrevented; })()`);
}
async function run() {
    let result = 0;
    try {
        session.defaultSession.webRequest.onBeforeRequest({ urls: ['http://*/*', 'https://*/*'] }, (_request, done) => { blocked += 1; done({ cancel: true }); });
        ipcMain.handle('vocab:load', () => ({ terms, progress, settings: { accent: 'en-US', speed: 1 }, storagePath: directory,
            audioInfo: { source: 'Kokoro', clips: 2328, texts: 1164 } }));
        ipcMain.handle('vocab:update-state', () => ({ currentVersion: '1.5.1', status: 'idle', message: 'Fixture only', routeMode: 'auto' }));
        ipcMain.handle('vocab:audio', (_event, text, accent) => { audioRequests.push({ text, accent }); return { status: 'missing' }; });
        ipcMain.handle('vocab:preferences', (_event, preferences) => { progress.quizPreferences = preferences; return progress; });
        ipcMain.handle('vocab:answer', (_event, answer) => { progress.attempts.push(answer); return progress; });
        ipcMain.handle('vocab:favorite', () => progress);
        window = new BrowserWindow({ show: false, width: 1220, height: 780,
            webPreferences: { preload: path.join(root, '.out/app/preload.cjs'), sandbox: true, contextIsolation: true } });
        await open();
        const renderedSources = new Set();
        const formulaTerms = terms.filter(t => [t.chinese, t.english, t.definition].some(text => text.includes('\\(') || text.includes('\\[')));
        for (const item of formulaTerms) {
            await term(item.id);
            const sources = await evaluate(`Array.from(document.querySelectorAll('.term-detail [data-math-source]')).map(node => {
                if (!node.querySelector('svg path')) throw new Error('Missing vector paths');
                return node.getAttribute('data-math-source');
            })`);
            for (const source of sources) renderedSources.add(source);
            assert.equal(await evaluate('document.querySelectorAll(".term-detail .math-error").length'), 0);
        }
        assert.equal(formulaTerms.length, 44); assert.equal(renderedSources.size, 63);
        for (const id of [323, 324, 325, 326, 112, 294, 207, 206, 260]) {
            await term(id);
            assert.ok(await evaluate('document.querySelectorAll(".term-detail .math-unit > svg path").length > 0'));
            assert.equal(await evaluate('document.querySelectorAll(".term-detail .math-error").length'), 0);
            if ([323, 324, 325].includes(id)) assert.equal(await selectAndCopy('.selectable-english'), terms.find(t => t.id === id).english);
            if ([326, 112, 294, 207, 206, 260].includes(id)) assert.equal(await selectAndCopy('.definition-text'), terms.find(t => t.id === id).definition);
        }
        await term(323);
        await evaluate('document.querySelector(".selectable-english .math-unit").click()');
        assert.equal(await selectAndCopy('.selectable-english .math-unit'), '\\(H_0\\)');
        assert.equal(await key('ArrowRight'), false, 'formula selection must retain its own arrow keys');
        assert.equal(await evaluate('document.querySelector(".term-reference").textContent'), '#323');
        await evaluate('window.getSelection().removeAllRanges()');
        await term(104);
        await evaluate(`(() => {const text=document.querySelector('.selectable-english').firstChild;const range=document.createRange();range.setStart(text,0);range.setEnd(text,9);const selection=window.getSelection();selection.removeAllRanges();selection.addRange(range);})()`);
        await waitFor('!document.querySelector("[data-testid=speak-selection]").disabled');
        await evaluate('document.querySelector("[data-testid=speak-selection]").click()');
        await pause(); assert.equal(audioRequests.at(-1).text, 'unbounded');
        await term(323); await selectAndCopy('.selectable-english .math-unit');
        await waitFor('!document.querySelector("[data-testid=speak-selection]").disabled');
        const before = audioRequests.length;
        await evaluate('document.querySelector("[data-testid=speak-selection]").click()'); await pause();
        assert.equal(audioRequests.length, before, 'formula source must not enter the audio lookup');
        await evaluate('window.getSelection().removeAllRanges()');
        await term(260);
        for (const width of [1000, 1220, 1440]) {
            window.setSize(width, 780);
            for (let sidebar = 0; sidebar < 2; sidebar += 1) {
                await evaluate('document.querySelector(".sidebar-toggle").click();document.querySelector(".definition-text").style.fontSize="26px"');
                await evaluate('new Promise(resolve=>requestAnimationFrame(()=>requestAnimationFrame(resolve)))');
                assert.equal(await evaluate(`(() => {const pane=document.querySelector('.detail-pane').getBoundingClientRect();const math=document.querySelector('.definition-text .math-unit');const r=math.getBoundingClientRect();return r.right<=pane.right+1&&r.width>0&&r.height>0;})()`), true);
                assert.equal(await evaluate(`(() => {const math=document.querySelector('.definition-text .math-unit');
                    if (math.scrollWidth <= math.clientWidth) return true;
                    math.scrollLeft=math.scrollWidth; const moved=math.scrollLeft>0; math.scrollLeft=0;return moved;})()`), true);
                const shot = await window.webContents.capturePage(); writeFileSync(path.join(output, `windows-math-${width}-${sidebar}.png`), shot.toPNG());
            }
        }
        await open();
        await evaluate('Array.from(document.querySelectorAll("button")).find(b=>b.textContent==="检测").click()');
        await waitFor('Boolean(document.querySelector("[data-testid=start-quiz]"))');
        await evaluate('document.querySelector("[data-testid=start-quiz]").click()');
        await waitFor('Boolean(document.querySelector("[data-testid=quiz-active]"))');
        let renderedOptions = 0;
        for (let n = 0; n < 4; n += 1) {
            renderedOptions += await evaluate('document.querySelectorAll(".answer-option .math-unit").length');
            await key(['a', '2', 'C', '4'][n]);
            await waitFor('Boolean(document.querySelector("[data-testid=answer-feedback]"))');
            assert.equal(progress.attempts.length, n + 1);
            assert.equal(await evaluate('document.querySelectorAll(".math-error").length'), 0);
            await key('Enter'); await pause();
        }
        assert.ok(renderedOptions > 0); assert.ok(await evaluate('Boolean(document.querySelector("[data-testid=quiz-summary]"))'));
        assert.equal(blocked, 0, 'learning and formula rendering must not even attempt HTTP');
        console.log(JSON.stringify({ passed: true, corpusTerms: formulaTerms.length, distinctRenderedFormulas: renderedSources.size, clipboardSource: true, formulaSelectionGuards: true,
            selectedWordAudioLookup: true, formulaExcludedFromAudio: true, widths: [1000,1220,1440], sidebarStates: 2,
            quizAnswers: 4, networkRequests: blocked, platform: process.platform, physicalWindowsTesting: false }, null, 2));
    } catch (error) { console.error(error); result = 1; }
    finally { window?.destroy(); await session.defaultSession.clearStorageData(); rmSync(directory, { recursive: true, force: true }); app.exit(result); }
}
app.whenReady().then(run).catch(error => { console.error(error); app.exit(1); });
