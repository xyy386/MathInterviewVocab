// Run after `npm run build`: node_modules/.bin/electron scripts/verify-keyboard.mjs
// Isolated Electron renderer checks; not a claim of Windows hardware acceptance.
import { app, BrowserWindow, ipcMain, session } from 'electron';
import assert from 'node:assert/strict';
import { mkdtempSync, readFileSync, rmSync } from 'node:fs';
import os from 'node:os';
import path from 'node:path';
import { fileURLToPath, pathToFileURL } from 'node:url';

const root = path.resolve(path.dirname(fileURLToPath(import.meta.url)), '..');
const directory = mkdtempSync(path.join(os.tmpdir(), 'math-vocab-keyboard-'));
app.setPath('userData', directory);
app.commandLine.appendSwitch('disable-background-networking');
const terms = JSON.parse(readFileSync(path.join(root, '../Resources/terms.json'), 'utf8'));
const { mathDisplay } = await import(pathToFileURL(path.join(root, '.out/core-audit.mjs')).href);
const preferences = { direction: 'mixed', scope: 'important', questionLength: 'ten', selectedSubjectID: 'analysis', includeMastered: true };
let progress = { attempts: [], favoriteIDs: [2, 104, 326], wrongIDs: [], masteredIDs: [], correctStreak: {}, quizPreferences: preferences };
const settings = { accent: 'en-US', speed: 1 };
let window;
let checks = 0;
let exitCode = 0;

const evaluate = source => window.webContents.executeJavaScript(source);
const pause = () => new Promise(resolve => setTimeout(resolve, 35));
async function waitFor(source) {
    for (let attempt = 0; attempt < 100; attempt += 1) {
        if (await evaluate(source)) return;
        await pause();
    }
    throw new Error(`Timed out: ${source}`);
}
const currentID = () => evaluate('Number(document.querySelector(".term-reference")?.textContent.replace("#", "")) || null');
async function key(value, options = {}, selector = null) {
    const prevented = await evaluate(`(() => {
        const target = ${JSON.stringify(selector)} ? document.querySelector(${JSON.stringify(selector)}) : document.activeElement;
        const event = new KeyboardEvent('keydown', { key: ${JSON.stringify(value)}, bubbles: true, cancelable: true, ...${JSON.stringify(options)} });
        (target || document.body).dispatchEvent(event);
        return event.defaultPrevented;
    })()`);
    await pause();
    return prevented;
}
async function check(label, run) {
    await run();
    checks += 1;
    console.log(`PASS ${label}`);
}
async function open(query = {}) {
    await window.loadFile(path.join(root, '.out/app/ui/index.html'), { query });
    await waitFor('Boolean(document.querySelector("[data-testid=glossary-welcome], [data-testid=settings-window]"))');
    // Hidden windows cannot have OS focus. These explicit fixtures exercise both
    // accepted and rejected focus/visibility branches without stealing user focus.
    await evaluate(`window.__qaFocused = true; window.__qaVisible = true;
        Object.defineProperty(document, 'hasFocus', { configurable: true, value: () => window.__qaFocused });
        Object.defineProperty(document, 'visibilityState', { configurable: true, get: () => window.__qaVisible ? 'visible' : 'hidden' }); void 0;`);
}

async function run() {
try {
    session.defaultSession.webRequest.onBeforeRequest({ urls: ['http://*/*', 'https://*/*'] }, (_details, done) => done({ cancel: true }));
    ipcMain.handle('vocab:load', () => ({ terms, progress, settings, storagePath: directory,
        audioInfo: { source: 'Kokoro-82M', clips: 2328, texts: 1164 } }));
    ipcMain.handle('vocab:preferences', (_event, value) => { progress.quizPreferences = value; return progress; });
    ipcMain.handle('vocab:answer', async (_event, value) => {
        assert.ok(!progress.attempts.some(attempt => attempt.id === value.id), 'duplicate record UUID');
        progress.attempts.push(value);
        await new Promise(resolve => setTimeout(resolve, 90));
        return progress;
    });
    ipcMain.handle('vocab:favorite', () => progress);
    ipcMain.handle('vocab:audio', () => ({ status: 'ready', url: 'data:audio/wav;base64,UklGRg==' }));
    ipcMain.handle('vocab:update-state', () => ({ status: 'idle', currentVersion: '1.2.0', message: 'QA only' }));
    window = new BrowserWindow({ show: false, width: 1220, height: 780,
        webPreferences: { preload: path.join(root, '.out/app/preload.cjs'), contextIsolation: true, sandbox: true } });
    await open();

    await check('four arrows, initial selection, boundaries and held-key suppression', async () => {
        assert.equal(await key('ArrowRight'), true); assert.equal(await currentID(), 1);
        await key('ArrowDown'); assert.equal(await currentID(), 2);
        await key('ArrowLeft'); assert.equal(await currentID(), 1);
        assert.equal(await key('ArrowUp'), true); assert.equal(await currentID(), 1);
        assert.equal(await key('ArrowRight', { repeat: true }), true); assert.equal(await currentID(), 1);
        await open(); await key('ArrowUp'); assert.equal(await currentID(), 326);
        assert.equal(await key('ArrowDown'), true); assert.equal(await currentID(), 326);
    });
    await check('list focus navigates once and sidebar resizing keeps its own keys', async () => {
        await evaluate('document.querySelector(\'[data-term-id="326"]\').focus()');
        await key('ArrowLeft'); assert.equal(await currentID(), 325);
        await key('Home'); assert.equal(await currentID(), 1);
        await key('End'); assert.equal(await currentID(), 326);
        await key('ArrowLeft'); assert.equal(await currentID(), 325);
        const before = await evaluate('document.querySelector("[role=separator]").getAttribute("aria-valuenow")');
        await evaluate('document.querySelector("[role=separator]").focus()');
        await key('ArrowRight'); assert.equal(await currentID(), 325);
        assert.equal(await evaluate('document.querySelector("[role=separator]").getAttribute("aria-valuenow")'), String(Number(before) + 10));
    });
    await check('editable, IME, selected text, modified keys, dialog, focus and visibility guards', async () => {
        await evaluate('document.querySelector("[data-testid=term-search]").focus()');
        assert.equal(await key('ArrowRight'), false); assert.equal(await currentID(), 325);
        await evaluate('document.activeElement.blur()');
        await evaluate('window.dispatchEvent(new CompositionEvent("compositionstart"))');
        assert.equal(await key('ArrowRight'), false);
        await evaluate('window.dispatchEvent(new CompositionEvent("compositionend"))');
        for (const options of [{ isComposing: true }, { ctrlKey: true }, { shiftKey: true }, { altKey: true }, { metaKey: true }]) {
            assert.equal(await key('ArrowRight', options), false);
        }
        await evaluate('window.getSelection().removeAllRanges(); const range = document.createRange(); range.selectNodeContents(document.querySelector(".detail-english")); window.getSelection().addRange(range)');
        assert.ok(await evaluate('window.getSelection().toString().length > 0'), 'fixture text selection exists');
        assert.equal(await key('ArrowLeft'), false);
        await evaluate('window.getSelection().removeAllRanges(); const dialog = document.createElement("div"); dialog.role = "dialog"; dialog.id = "qa-dialog"; document.body.append(dialog)');
        assert.equal(await key('ArrowLeft'), false);
        await evaluate('document.querySelector("#qa-dialog").remove(); window.__qaFocused = false');
        assert.equal(await key('ArrowLeft'), false);
        await evaluate('window.__qaFocused = true; window.__qaVisible = false');
        assert.equal(await key('ArrowLeft'), false);
        await evaluate('window.__qaVisible = true');
        assert.equal(await currentID(), 325);
        await evaluate(`window.__qaPrevent = event => event.preventDefault(); document.body.addEventListener('keydown', window.__qaPrevent)`);
        assert.equal(await key('ArrowLeft'), true); assert.equal(await currentID(), 325);
        await evaluate(`document.body.removeEventListener('keydown', window.__qaPrevent)`);
    });
    await check('filtered order, open menu protection and collapsed sidebar navigation', async () => {
        await evaluate('document.querySelector(".filter-menu summary").click()');
        assert.equal(await key('ArrowLeft'), false); assert.equal(await currentID(), 325);
        await evaluate('Array.from(document.querySelectorAll(".filter-check")).find(el => el.textContent.includes("收藏")).click()');
        await pause();
        await evaluate('document.activeElement.blur()');
        assert.equal(await currentID(), null);
        await key('ArrowDown'); assert.equal(await currentID(), 2);
        await key('ArrowRight'); assert.equal(await currentID(), 104);
        await evaluate('document.querySelector(".sidebar-toggle").click()');
        await pause();
        await key('ArrowDown'); assert.equal(await currentID(), 326);
        await key('ArrowDown'); assert.equal(await currentID(), 326);
        await key('ArrowLeft'); assert.equal(await currentID(), 104);
        await evaluate('document.querySelector(".sidebar-toggle").click()');
        await pause();
        assert.ok(await evaluate(`(() => {
            const row = document.querySelector('[data-term-id="104"]').getBoundingClientRect();
            const list = document.querySelector('.term-list').getBoundingClientRect();
            return row.top >= list.top && row.bottom <= list.bottom;
        })()`));
    });
    await check('empty search consumes no navigation and clearing search restores filtered keyboard navigation', async () => {
        await evaluate(`(() => { const input = document.querySelector('[data-testid=term-search]'); input.focus();
            Object.getOwnPropertyDescriptor(HTMLInputElement.prototype, 'value').set.call(input, '__qa_no_matching_term__');
            input.dispatchEvent(new Event('input', { bubbles: true })); })()`);
        await pause();
        await evaluate('document.activeElement.blur()');
        assert.equal(await currentID(), null);
        assert.equal(await key('ArrowRight'), false);
        await evaluate(`(() => { const input = document.querySelector('[data-testid=term-search]');
            Object.getOwnPropertyDescriptor(HTMLInputElement.prototype, 'value').set.call(input, '');
            input.dispatchEvent(new Event('input', { bubbles: true })); })()`);
        await pause();
        await key('ArrowDown'); assert.equal(await currentID(), 2);
        await key('ArrowRight'); assert.equal(await currentID(), 104);
    });
    await check('changing a term stops playback, clears selection and resets reading position', async () => {
        await evaluate(`window.__qaPauses = 0; window.Audio = class {
            play() { return Promise.resolve(); } pause() { window.__qaPauses += 1; }
            removeAttribute() {} load() {}
        }; document.querySelector('[data-testid=speak-full]').click()`);
        await pause();
        await evaluate('document.querySelector(".detail-pane").scrollTop = 400');
        await key('ArrowRight'); assert.equal(await currentID(), 326);
        assert.ok(await evaluate('window.__qaPauses > 0'));
        assert.equal(await evaluate('document.querySelector(".detail-pane").scrollTop'), 0);
        assert.equal(await evaluate('window.getSelection().toString()'), '');
    });
    await check('active quiz does not skip unanswered questions and records only one rapid answer', async () => {
        await evaluate('document.querySelectorAll(".main-navigation button")[1].click()');
        await pause();
        await evaluate('document.querySelector("[data-testid=start-quiz]").click()');
        await waitFor('Boolean(document.querySelector("[data-testid=quiz-active]"))');
        await key('ArrowRight'); await key('ArrowDown'); await key('Enter');
        assert.match(await evaluate('document.querySelector(".quiz-counter").textContent'), /第 1 /);
        await evaluate(`document.body.dispatchEvent(new KeyboardEvent('keydown', { key: 'a', bubbles: true, cancelable: true }));
            document.body.dispatchEvent(new KeyboardEvent('keydown', { key: '2', bubbles: true, cancelable: true }));`);
        await pause();
        assert.equal(progress.attempts.length, 1);
        await waitFor('!document.querySelector("[data-testid=next-question]").disabled');
        await evaluate(`document.body.dispatchEvent(new KeyboardEvent('keydown', { key: 'ArrowRight', bubbles: true, cancelable: true }));
            document.querySelector('[data-testid=next-question]').click();
            document.body.dispatchEvent(new KeyboardEvent('keydown', { key: 'ArrowDown', bubbles: true, cancelable: true }));`);
        await pause();
        assert.match(await evaluate('document.querySelector(".quiz-counter").textContent'), /第 2 /);
        assert.equal(await evaluate('Boolean(document.querySelector("[data-testid=answer-feedback]"))'), false);
        assert.equal(progress.attempts.length, 1);
    });
    await check('A-D, shifted uppercase and numpad digits select displayed options; last key shows summary', async () => {
        const inputs = [ ['B', { shiftKey: true }, 2], ['c', {}, 3], ['D', {}, 4], ['1', { code: 'Numpad1' }, 1],
            ['2', { code: 'Numpad2' }, 2], ['3', { code: 'Numpad3' }, 3], ['4', { code: 'Numpad4' }, 4], ['a', {}, 1], ['b', {}, 2] ];
        for (const [index, [value, options, position]] of inputs.entries()) {
            const expected = await evaluate(`document.querySelector('[data-testid="answer-option-${position}"]').textContent.slice(1)`);
            await key(value, options);
            await waitFor('Boolean(document.querySelector("[data-testid=answer-feedback]")) && !document.querySelector("[data-testid=next-question]").disabled');
            const answered = progress.attempts.at(-1);
            const answerTerm = terms.find(term => term.id === answered.selectedOptionID);
            assert.ok(expected.includes(answered.direction === 'zhToEn' ? mathDisplay(answerTerm.english) : answerTerm.chinese));
            assert.equal(progress.attempts.length, index + 2);
            assert.equal(await key(value, { ...options, repeat: true }), true);
            assert.equal(progress.attempts.length, index + 2);
            await key(['Enter', 'ArrowRight', 'ArrowDown'][index % 3]);
        }
        assert.ok(await evaluate('Boolean(document.querySelector("[data-testid=quiz-summary]"))'));
        await key('a'); await key('ArrowRight'); assert.equal(progress.attempts.length, 10);
    });
    await check('settings window has no vocabulary or quiz keyboard handlers', async () => {
        await open({ window: 'settings' });
        await key('a'); await key('1'); await key('ArrowRight'); await key('Enter');
        assert.equal(progress.attempts.length, 10);
        assert.ok(await evaluate('Boolean(document.querySelector("[data-testid=settings-window]"))'));
    });
    console.log(JSON.stringify({ passed: checks, platform: process.platform,
        kind: 'isolated Electron renderer DOM events with focus/visibility fixtures', userDataTouched: false }));
} catch (error) {
    console.error(error);
    exitCode = 1;
} finally {
    window?.destroy();
    rmSync(directory, { recursive: true, force: true });
    app.exit(exitCode);
}
}

app.whenReady().then(run);
