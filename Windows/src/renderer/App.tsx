import { useEffect, useState, type CSSProperties, type ReactNode } from 'react';
import type { LoadPayload } from '../shared/api';
import type { ProgressData, QuizAttempt, QuizPreferences, SpeechSettings } from '../shared/core';
import { GlossaryView } from './Glossary';
import { ProgressView, QuizView } from './Learning';
import { SettingsView } from './Settings';
import { Icon } from './ui';
import { useAudio } from './useAudio';

type MainTab = 'glossary' | 'quiz' | 'progress';
const tabs: { id: MainTab; label: string; icon: string }[] = [
  { id: 'glossary', label: '词汇', icon: 'book' },
  { id: 'quiz', label: '检测', icon: 'checkcircle' },
  { id: 'progress', label: '学习记录', icon: 'chart' },
];

export function App() {
  const [payload, setPayload] = useState<LoadPayload | null>(null);
  const [error, setError] = useState('');
  const [notice, setNotice] = useState('');
  const [tab, setTab] = useState<MainTab>('glossary');
  const [sidebarOpen, setSidebarOpen] = useState(true);
  const [sidebarWidth, setSidebarWidth] = useState(380);
  const isSettings = new URLSearchParams(window.location.search).get('window') === 'settings';
  const audio = useAudio(payload?.settings ?? { accent: 'en-US', speed: 1 });

  useEffect(() => { document.title = isSettings ? '“数学面试词汇”设置' : '数学面试词汇'; }, [isSettings]);

  useEffect(() => {
    let active = true;
    if (!window.vocab) { setError('请从“数学面试词汇”桌面应用启动。'); return; }
    const unsubscribe = window.vocab.onDataChanged(change => {
      setPayload(previous => previous ? { ...previous, ...change } : previous);
      setNotice('');
    });
    window.vocab.load().then(result => { if (active) setPayload(result); })
      .catch(error => { if (active) setError(message(error)); });
    return () => { active = false; unsubscribe(); };
  }, []);

  useEffect(() => {
    if (isSettings) return;
    const keyDown = (event: KeyboardEvent) => {
      const target = event.target as HTMLElement | null;
      if (target?.isContentEditable || ['INPUT', 'TEXTAREA', 'SELECT'].includes(target?.tagName ?? '')) return;
      if (!(event.ctrlKey || event.metaKey) || event.altKey || event.shiftKey) return;
      const next = tabs[Number(event.key) - 1];
      if (next) { event.preventDefault(); audio.stop(); setTab(next.id); }
      if (event.key === ',') { event.preventDefault(); void window.vocab.openSettings(); }
    };
    window.addEventListener('keydown', keyDown);
    return () => window.removeEventListener('keydown', keyDown);
  }, [isSettings, audio.stop]);

  async function mutate<T>(operation: () => Promise<T>, apply: (value: T) => void): Promise<void> {
    try { apply(await operation()); setNotice(''); }
    catch (error) { setNotice(message(error)); throw error; }
  }
  const setProgress = (progress: ProgressData) => setPayload(previous => previous ? { ...previous, progress } : previous);
  const onFavorite = (id: number) => mutate(() => window.vocab.toggleFavorite(id), setProgress);
  const onPreferences = (preferences: QuizPreferences) => mutate(() => window.vocab.updateQuizPreferences(preferences), setProgress);
  const onAnswer = (attempt: QuizAttempt) => mutate(() => window.vocab.recordAnswer(attempt), setProgress);
  const onSave = (settings: SpeechSettings) => mutate(() => window.vocab.saveSettings(settings), settings => setPayload(previous => previous ? { ...previous, settings } : previous));
  const onReset = () => mutate(() => window.vocab.resetProgress(), setProgress);

  if (error || !payload) return <div className="app-shell"><WindowChrome isSettings={isSettings}/><main className="startup-state"><Icon name={error ? 'warning' : 'book'} size={32}/><h1>{error ? '无法启动应用' : '正在加载词汇…'}</h1>{error && <p>{error}</p>}</main></div>;
  if (isSettings) return <div className="settings-shell"><WindowChrome isSettings/><SettingsView settings={payload.settings} progress={payload.progress} audio={audio} audioInfo={payload.audioInfo} onSave={onSave} onReset={onReset} error={notice || payload.persistenceError || undefined}/></div>;

  const sidebar = tab === 'glossary' && sidebarOpen ? sidebarWidth : 0;
  return <div className="app-shell" style={{ '--sidebar-width': `${sidebar}px` } as CSSProperties}>
    <WindowChrome>
      {tab === 'glossary' && <button type="button" className="icon-button sidebar-toggle" title={sidebarOpen ? '隐藏词汇侧栏' : '显示词汇侧栏'} aria-label={sidebarOpen ? '隐藏词汇侧栏' : '显示词汇侧栏'} style={{ left: sidebarOpen ? sidebarWidth - 40 : 88 }} onClick={() => setSidebarOpen(value => !value)}><Icon name="sidebar" size={18}/></button>}
      <span className={`window-title ${sidebar ? 'with-sidebar' : ''}`}>数学面试词汇</span>
      <nav className="main-navigation" aria-label="主导航">{tabs.map((item, index) => <button type="button" key={item.id} aria-current={tab === item.id ? 'page' : undefined} title={`Ctrl+${index + 1}`} className={tab === item.id ? 'is-selected' : ''} onClick={() => { audio.stop(); setTab(item.id); }}><Icon name={item.icon} size={14}/>{item.label}</button>)}</nav>
      <button type="button" className="icon-button settings-toggle" aria-label="设置" title="设置 (Ctrl+,)" onClick={() => void window.vocab.openSettings()}><Icon name="gear" size={18}/></button>
    </WindowChrome>
    {notice && <div className="save-notice" role="alert"><Icon name="warning"/>{notice}<button type="button" aria-label="关闭提示" onClick={() => setNotice('')}><Icon name="cross" size={12}/></button></div>}
    <main className="main-content">
      {tab === 'glossary' && <GlossaryView terms={payload.terms} progress={payload.progress} settings={payload.settings} audio={audio} onFavorite={onFavorite} sidebarOpen={sidebarOpen} sidebarWidth={sidebarWidth} onSidebarResize={setSidebarWidth}/>}
      {tab === 'quiz' && <QuizView terms={payload.terms} progress={payload.progress} settings={payload.settings} audio={audio} onPreferences={onPreferences} onAnswer={onAnswer}/>}
      {tab === 'progress' && <ProgressView terms={payload.terms} progress={payload.progress} audio={audio} persistenceError={payload.persistenceError || undefined}/>}
    </main>
  </div>;
}

function WindowChrome({ children, isSettings = false }: { children?: ReactNode; isSettings?: boolean }) {
  return <header className={`window-chrome ${isSettings ? 'settings-chrome' : ''}`} onDoubleClick={event => { if (event.target === event.currentTarget && !isSettings) void window.vocab.windowAction('maximize'); }}>
    <div className="window-controls">
      <button type="button" className="window-close" aria-label="关闭窗口" onClick={() => void window.vocab.windowAction('close')}><span>×</span></button>
      <button type="button" className="window-minimize" aria-label="最小化窗口" onClick={() => void window.vocab.windowAction('minimize')}><span>−</span></button>
      <button type="button" className="window-maximize" aria-label="最大化或还原窗口" disabled={isSettings} onClick={() => void window.vocab.windowAction('maximize')}><span>+</span></button>
    </div>
    {isSettings && <strong className="settings-window-title">“数学面试词汇”设置</strong>}{children}
  </header>;
}

function message(error: unknown): string {
  return error instanceof Error ? error.message : '本地记录保存失败，请检查磁盘权限。';
}
