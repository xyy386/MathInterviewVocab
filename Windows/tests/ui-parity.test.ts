import test from 'node:test';
import assert from 'node:assert/strict';
import { readFileSync } from 'node:fs';
import { createElement, type ComponentType } from 'react';
import { renderToStaticMarkup } from 'react-dom/server';
import { GlossaryView, type GlossaryViewProps } from '../src/renderer/Glossary';
import { QuizView, ProgressView } from '../src/renderer/Learning';
import { SettingsView } from '../src/renderer/Settings';
import { KnowledgeAxis, PronunciationPanel } from '../src/renderer/ui';
import { defaultProgress, type ProgressData, type SpeechSettings, type Term } from '../src/shared/core';
import type { AudioController } from '../src/renderer/useAudio';

const terms = JSON.parse(readFileSync(new URL('../../Resources/terms.json', import.meta.url), 'utf8')) as Term[];
const progress = defaultProgress();
const settings: SpeechSettings = { accent: 'en-US', speed: 1 };
const audio: AudioController = {
    state: { kind: 'idle', message: '' },
    play: async () => undefined,
    stop: () => undefined,
};
const noop = async () => undefined;
const source = (name: string) => readFileSync(new URL(`../src/renderer/${name}`, import.meta.url), 'utf8');
const mainSource = readFileSync(new URL('../src/main/main.ts', import.meta.url), 'utf8');

function render<Props extends object>(component: ComponentType<Props>, props: Props): string {
    return renderToStaticMarkup(createElement(component, props));
}

function count(html: string, pattern: RegExp): number {
    return [...html.matchAll(pattern)].length;
}

function assertOrder(text: string, fragments: string[]): void {
    let previous = -1;
    for (const fragment of fragments) {
        const index = text.indexOf(fragment, previous + 1);
        assert.ok(index > previous, `Missing or out-of-order fragment: ${fragment}`);
        previous = index;
    }
}

const glossaryProps: GlossaryViewProps = {
    terms, progress, settings, audio, onFavorite: noop,
    sidebarOpen: true, sidebarWidth: 380, onSidebarResize: () => undefined,
};
const glossaryHTML = render(GlossaryView, glossaryProps);
const quizHTML = render(QuizView, { terms, progress, settings, audio, onPreferences: noop, onAnswer: noop });
const progressHTML = render(ProgressView, { terms, progress, audio });
const settingsHTML = render(SettingsView, {
    settings, progress, audio,
    audioInfo: { source: 'Kokoro-82M', clips: 2328, texts: 1164 },
    onSave: noop, onReset: noop,
});

// 这是 SSR／源码契约测试，不冒充真实窗口、选区或音频交互验收。
test('Mac-parity modules render on the server without browser globals', () => {
    assert.equal(typeof window, 'undefined');
    assert.equal(typeof document, 'undefined');
    for (const html of [glossaryHTML, quizHTML, progressHTML, settingsHTML]) assert.ok(html.length > 100);
});

test('glossary starts at the Mac welcome page without selecting any term', () => {
    assert.match(glossaryHTML, /data-testid="glossary-welcome"/);
    assert.doesNotMatch(glossaryHTML, /data-testid="term-detail"/);
    assert.equal(count(glossaryHTML, /role="option"/g), 326);
    assert.equal(count(glossaryHTML, /aria-selected="true"/g), 0);
    assert.equal(count(glossaryHTML, /aria-selected="false"/g), 326);
    assert.match(glossaryHTML, /<div(?=[^>]*role="listbox")(?=[^>]*tabindex="0")[^>]*>/);
});

test('glossary renders exactly one sidebar and one header per subject in Mac order', () => {
    assert.equal(count(glossaryHTML, /<aside\b/g), 1);
    const headers = [...glossaryHTML.matchAll(/<h3 class="list-section-title">([^<]+)<\/h3>/g)].map(match => match[1]);
    assert.deepEqual(headers, ['常用数学学科名称', '数学分析', '高等代数', '概率论', '数理统计']);
    assert.equal(count(glossaryHTML, /class="term-important"/g), 130);
});

test('Mac search, subject menu and filter labels retain their exact text', () => {
    assert.match(glossaryHTML, /aria-label="搜索中文、英文或释义"/);
    assert.match(glossaryHTML, /全部科目（326）/);
    assertOrder(glossaryHTML, ['仅看重点', '仅看收藏', '仅看错题', '清除筛选']);
    assert.match(glossaryHTML, /显示 326 \/ 326 个词汇/);
    assert.doesNotMatch(glossaryHTML, /词汇索引|MATHEMATICS INTERVIEW|TERM ·/);
});

test('collapsed glossary removes the single index without introducing another navigation sidebar', () => {
    const html = render(GlossaryView, { ...glossaryProps, sidebarOpen: false });
    assert.equal(count(html, /<aside\b/g), 0);
    assert.match(html, /sidebar-collapsed/);
    assert.match(html, /data-testid="glossary-welcome"/);
});

test('welcome content follows the Mac title, metrics and five learning blocks', () => {
    const welcome = glossaryHTML.slice(glossaryHTML.indexOf('data-testid="glossary-welcome"'));
    assertOrder(welcome, ['数学专业面试', '英文专业词汇汇总', '从左侧选择一个词汇，查看规范英文、中文释义并播放发音。', '全部词汇', '重点词汇', '已掌握', '五个学习板块']);
    assert.equal(count(welcome, /class="welcome-subject-row"/g), 5);
});

test('favorite and mastery indicators can coexist on the same glossary row', () => {
    const marked: ProgressData = { ...defaultProgress(), favoriteIDs: [1], masteredIDs: [1] };
    const html = render(GlossaryView, { ...glossaryProps, progress: marked });
    const rowStart = html.indexOf('data-testid="term-row-1"');
    const row = html.slice(rowStart, html.indexOf('</button>', rowStart));
    assert.match(row, /aria-label="已收藏"/);
    assert.match(row, /aria-label="已掌握"/);
});

test('detail source contract keeps English, IPA and speech/favorite actions inside its first card', () => {
    const detail = source('Glossary.tsx').slice(source('Glossary.tsx').indexOf('function TermDetail('));
    const firstCardStart = detail.indexOf('<AcademicCard emphasized={term.important} className="term-main-card">');
    assert.ok(firstCardStart > 0);
    const firstCard = detail.slice(firstCardStart, detail.indexOf('</AcademicCard>', firstCardStart));
    assertOrder(firstCard, ['className="term-title"', '<SelectableEnglish', '<PronunciationPanel', '朗读完整词组', '朗读所选', 'favorite-button']);
    assertOrder(detail, ['term-main-card', 'definition-card', 'term-progress-card']);
    assert.match(detail, /先双击单词或拖动选择一段英文/);
});

test('quiz setup matches the Mac heading and its three setting cards', () => {
    assert.match(quizHTML, /用主动选择检验记忆/);
    assert.match(quizHTML, /每题四个选项，答题后立即查看释义和英文发音。/);
    assertOrder(quizHTML, ['direction-card', '出题方向', 'scope-card', '练习范围', 'length-card', '题目数量', '开始检测']);
    assert.equal(count(quizHTML, /quiz-setting-card/g), 3);
    assert.doesNotMatch(quizHTML, /ASSESSMENT|常见面试术语|个人练习清单|关闭后，实际出题数量/);
});

test('quiz setup keeps exact direction, scope and question-length choices', () => {
    assertOrder(quizHTML, ['中文 → 英文', '英文 → 中文', '双向混合']);
    assertOrder(quizHTML, ['重点词汇', '全部词汇', '指定科目', '错题复习', '我的收藏']);
    assertOrder(quizHTML, ['10 题', '20 题', '50 题', '全部', '包含已经掌握的词汇']);
    assert.equal(count(quizHTML, /class="scope-option/g), 5);
    assert.match(quizHTML, /130 个词/);
    assert.match(quizHTML, /326 个词/);
    assert.doesNotMatch(quizHTML, /data-testid="quiz-subject"/);
});

test('saved subject preference conditionally reveals the Mac subject menu', () => {
    const saved = { ...defaultProgress(), quizPreferences: { ...progress.quizPreferences, scope: 'subject' as const, selectedSubjectID: 'probability' } };
    const html = render(QuizView, { terms, progress: saved, settings, audio, onPreferences: noop, onAnswer: noop });
    assert.match(html, /data-testid="quiz-subject"/);
    assert.match(html, /<option value="probability" selected="">概率论（27）<\/option>/);
});

test('empty favorite scope shows the same Mac guidance instead of a start button', () => {
    const saved = { ...defaultProgress(), quizPreferences: { ...progress.quizPreferences, scope: 'favorites' as const } };
    const html = render(QuizView, { terms, progress: saved, settings, audio, onPreferences: noop, onAnswer: noop });
    assert.match(html, /当前范围没有可练习的词汇/);
    assert.match(html, /先在词汇详情中收藏想要练习的术语。/);
    assert.doesNotMatch(html, /data-testid="start-quiz"/);
});

test('quiz active-source contract preserves labels and routes guarded keyboard actions through shared helpers', () => {
    const learning = source('Learning.tsx');
    const keyboard = source('keyboard.ts');
    assert.match(learning, /String\.fromCharCode\(65 \+ index\)/);
    assert.match(learning, /quizOptionIndex\(key\)/);
    assert.match(learning, /isQuizAdvanceKey\(key\)/);
    assert.match(learning, /A–D／1–4 选择 · Enter／→／↓ 继续/);
    assert.match(learning, /answerLock\.current = true/);
    assert.match(learning, /advancedQuestionIndex\.current = questionIndex/);
    assert.match(keyboard, /event\.ctrlKey \|\| event\.metaKey \|\| event\.altKey/);
    assert.match(keyboard, /\[role="dialog"\]/);
    assert.match(learning, /<AcademicCard emphasized className="answer-feedback"/);
});

test('summary-source contract retains the Mac zero-answer and 90/70 percent result wording', () => {
    const learning = source('Learning.tsx');
    assertOrder(learning, ['准备好后再开始', 'accuracy >= 90', '掌握得很扎实', 'accuracy >= 70', '再复习一轮就更稳了', '从错题开始巩固']);
    assert.match(learning, /本轮尚未作答/);
    assert.match(learning, /本轮完成 \$\{answeredCount\} 题/);
    assert.doesNotMatch(learning, /RESULT|掌握得很好|从错题重新出发|正确答案如下|继续保持/);
});

test('progress title, labels and card order match the Mac dashboard', () => {
    assertOrder(progressHTML, ['<h1>学习记录</h1>', '记录只保存在当前设备，不需要账号或网络。', 'progress-metrics', '已检测词汇', '已掌握', '待复习', '总正确率', '总体掌握度', '分科学习进度', '当前没有待复习词汇']);
    assert.equal(count(progressHTML, /class="academic-card\s+progress-metric"/g), 4);
    assert.equal(count(progressHTML, /class="subject-progress-row"/g), 5);
    assert.doesNotMatch(progressHTML, /LEARNING RECORD|OVERALL MASTERY|把每次练习变成下一步|答题正确率|整体掌握度|达到连续答对标准|共 0 次作答/);
});

test('no-mistake dashboard uses the single left-aligned success card', () => {
    assert.match(progressHTML, /progress-no-mistakes/);
    assert.doesNotMatch(progressHTML, /progress-review|最多展示前 12 个错题/);
});

test('dashboard review list caps at 12 with dividers and exact overflow guidance', () => {
    const wrong = { ...defaultProgress(), wrongIDs: terms.slice(0, 13).map(term => term.id) };
    const html = render(ProgressView, { terms, progress: wrong, audio });
    assert.match(html, /待复习错题/);
    assert.match(html, /13 个/);
    assert.equal(count(html, /class="review-row"/g), 12);
    assert.equal(count(html, /class="review-divider"/g), 11);
    assert.match(html, /另有 1 个错题，可在“词汇”中选择“仅看错题”。/);
    assert.doesNotMatch(html, /progress-no-mistakes|最多展示前/);
});

test('progress persistence failure is visible in a dedicated error card', () => {
    const html = render(ProgressView, { terms, progress, audio, persistenceError: '磁盘只读' });
    assert.match(html, /class="academic-card\s+persistence-error"/);
    assert.match(html, /本地记录保存失败：磁盘只读/);
});

test('settings matches Mac immediate-save fields without Save/Cancel footer', () => {
    assertOrder(settingsHTML, ['英语发音', '默认口音', '美式英语', '英式英语', '语速', '试听美式', '试听英式', '离线语音', '软件更新', '数据', '学习记录', '仅保存在当前设备', '账号', '不需要', '学习与发音联网', '清空本机学习记录']);
    assert.equal(count(settingsHTML, /<option\b/g), 3);
    assertOrder(settingsHTML, ['value="0.75"', '0.75×', 'value="1"', '1×', 'value="1.25"', '1.25×']);
    assert.doesNotMatch(settingsHTML, /<footer|保存设置|取消|Google|需要联网|在线语音/);
    const settingsSource = source('Settings.tsx');
    assert.match(settingsSource, /window\.vocab\.confirmReset\(\)/);
    assert.match(settingsSource, /if \(!confirmed\) return/);
});

test('pronunciation panel is one section with US above UK and the correct default marker', () => {
    const html = render(PronunciationPanel, { term: terms[103], settings: { accent: 'en-GB' as const, speed: 1 }, audio });
    assert.equal(count(html, /aria-label="英美音标"/g), 1);
    assert.equal(count(html, /class="pronunciation-row/g), 2);
    assertOrder(html, ['英美音标', '内置离线发音', '>US<', '美式', '美式朗读', '>UK<', '英式', '默认', '英式朗读']);
    assert.match(html, /<span class="accent-stamp is-default">UK<\/span>/);
    assert.match(html, /ʌnˈbaʊndɪd ˈfʌŋkʃən/);
    assert.doesNotMatch(html, /Google|在线发音|需要联网/);
});

test('compact pronunciation mode and explicit missing-audio feedback are preserved', () => {
    const html = render(PronunciationPanel, { term: terms[0], settings, compact: true,
        audio: { ...audio, state: { kind: 'missing' as const, message: '内置语音中暂未找到该选区。' } } });
    assert.match(html, /pronunciation-panel is-compact/);
    assert.match(html, /pronunciation-status is-error/);
    assert.match(html, /内置语音中暂未找到该选区。/);
});

test('knowledge axis has all three Mac nodes and rounds the displayed percentage', () => {
    const html = render(KnowledgeAxis, { value: 2 / 3 });
    assert.equal(count(html, /class="axis-node /g), 3);
    assert.equal(count(html, /class="axis-marker"/g), 1);
    assertOrder(html, ['陌生', '学习中', '已掌握']);
    assert.match(html, /aria-label="掌握进度 67%"/);
    assert.match(render(KnowledgeAxis, { value: 2 }), /--fraction:1/);
    assert.match(render(KnowledgeAxis, { value: -1 }), /--fraction:0/);
});

test('settings-window source contract is independent, reusable and synchronizes saved data', () => {
    assert.match(mainSource, /settingsWindow = new BrowserWindow/);
    assert.match(mainSource, /width: 480, height: 680, resizable: false/);
    assert.match(mainSource, /index\.html\?window=settings/);
    assert.match(mainSource, /settingsWindow\.show\(\); settingsWindow\.focus\(\); return/);
    assert.match(mainSource, /vocab:data-changed/);
    assert.match(mainSource, /broadcast\(\{ settings: value \}\)/);
    assert.match(mainSource, /vocab:stop-audio/);
});

test('settings source contract serializes rapid edits and rolls back the latest failed optimistic edit', () => {
    const settingsSource = source('Settings.tsx');
    assert.match(settingsSource, /const nextSettings = \{ \.\.\.pendingSettings\.current, \.\.\.patch \}/);
    assert.match(settingsSource, /saveQueue\.current = saveQueue\.current\.catch/);
    assert.match(settingsSource, /confirmedSettings\.current = nextSettings/);
    assert.match(settingsSource, /revision === saveRevision\.current/);
    assert.match(settingsSource, /pendingSettings\.current = confirmedSettings\.current/);
    assert.match(settingsSource, /setDisplaySettings\(confirmedSettings\.current\)/);
});

test('IPC source contract trusts only registered top-level app windows', () => {
    assert.match(mainSource, /windows\(\)\.some\(window => window\.webContents === event\.sender\)/);
    assert.match(mainSource, /event\.senderFrame !== event\.sender\.mainFrame/);
    assert.match(mainSource, /vocab:\/\/bundle\/index\.html/);
    assert.match(mainSource, /nodeIntegration: false, contextIsolation: true/);
    assert.match(mainSource, /sandbox: true, webSecurity: true/);
});

test('learning and pronunciation remain offline and do not bundle Apple font-dependent icon art', () => {
    assert.match(mainSource, /urls: \['http:\/\/\*\/\*', 'https:\/\/\*\/\*'\]/);
    assert.match(mainSource, /callback\(\{ cancel: true \}\)/);
    assert.doesNotMatch(source('useAudio.ts'), /fetch\(|https?:\/\/|google/i);
    const icon = readFileSync(new URL('../resources/icon.svg', import.meta.url), 'utf8');
    assert.doesNotMatch(icon, /<text\b|SF Symbols|New York|SF Mono|font-family/);
    const css = source('styles.css');
    for (const color of ['#14213d', '#2e5aac', '#f7f9fc', '#d9e4f2', '#1c7c54', '#b54343']) assert.ok(css.toLowerCase().includes(color));
    assert.doesNotMatch(css, /min-width:\s*1000px|min-height:\s*680px/);
});

test('software update settings keep networking opt-in, actual app version and main-process-only downloads', () => {
    assert.match(settingsHTML, /data-testid="software-update"/);
    assertOrder(settingsHTML, ['软件更新', '当前版本', '检查更新', '词库与发音始终离线']);
    assert.match(settingsHTML, /仅检查和下载更新时联网/);
    assert.match(mainSource, /new SoftwareUpdater\(app\.getVersion\(\), app\.getPath\('downloads'\)/);
    assert.match(mainSource, /handle\('vocab:update-download', \(\) => updater\.download\(\)\)/);
    assert.doesNotMatch(source('SoftwareUpdate.tsx'), /fetch\(|https?:\/\//);
});
