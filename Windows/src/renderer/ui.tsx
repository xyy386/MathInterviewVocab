import { useEffect, useRef, type CSSProperties, type ReactNode, type MouseEvent } from 'react';
import { mathDisplay, sanitizeSelection, type Accent, type SpeechSettings, type Term } from '../shared/core';
import type { AudioController } from './useAudio';

// 自绘线性图标，不分发 Apple 字体或 SF Symbols 资源。
const paths: Record<string, ReactNode> = {
  book: <><path d="M3 4h7l2 2 2-2h7v15h-7l-2 2-2-2H3zM12 6v15" /></>,
  check: <path d="m5 12 4 4L19 6" />,
  chart: <><path d="M4 3v18h17M7 15l4-4 4 2 5-7" /></>,
  gear: <><path d="m10 3-.8 3-2.6 1.5-3-.7-1.5 2.6 2.2 2.3v3L2 17l1.5 2.6 3-.7L9.2 20l.8 3h3l.8-3 2.6-1.5 3 .7 1.5-2.6-2.2-2.3v-3L21 9l-1.5-2.6-3 .7L13.8 6 13 3z" transform="translate(0 -1) scale(1 .92)"/><circle cx="11.5" cy="11.5" r="3" /></>,
  sidebar: <><rect x="3" y="4" width="18" height="16" rx="2"/><path d="M9 4v16M5.5 8h1M5.5 11h1"/></>,
  search: <><circle cx="10" cy="10" r="6.5"/><path d="m15 15 6 6"/></>,
  star: <path d="m12 3 2.8 5.7 6.2.9-4.5 4.4 1.1 6.2L12 17.3l-5.6 2.9 1.1-6.2L3 9.6l6.2-.9z"/>,
  speaker: <><path d="M4 9h4l5-4v14l-5-4H4zM16 8a6 6 0 0 1 0 8M19 5a10 10 0 0 1 0 14"/></>,
  selection: <><path d="M7 3h10M12 3v18M7 21h10M4 8v8M20 8v8"/></>,
  rotate: <><path d="M5 8a8 8 0 1 1-1 7M5 3v6h6"/></>,
  scope: <><circle cx="12" cy="12" r="9"/><circle cx="12" cy="12" r="5"/><circle cx="12" cy="12" r="1"/></>,
  grid: <><rect x="3" y="3" width="7" height="7" rx="1"/><rect x="14" y="3" width="7" height="7" rx="1"/><rect x="3" y="14" width="7" height="7" rx="1"/><rect x="14" y="14" width="7" height="7" rx="1"/></>,
  bookmark: <path d="M6 3h12v18l-6-4-6 4z"/>,
  books: <><path d="M3 4h5v17H3zM10 3h4v18h-4zM16 5l4-1 4 16-4 1z"/></>,
  arrows: <><path d="M3 7h18m-4-4 4 4-4 4M21 17H3m4-4-4 4 4 4"/></>,
  number: <><path d="m9 3-3 18M18 3l-3 18M3 9h18M2 15h18"/></>,
  arrow: <path d="M3 12h18m-7-7 7 7-7 7"/>,
  play: <path d="m7 4 13 8-13 8z"/>,
  question: <path d="M8 7a4 4 0 1 1 6 3.5c-2 1-2 2-2 4M12 19h.01"/>,
  cross: <path d="m6 6 12 12M18 6 6 18"/>,
  checkcircle: <><circle cx="12" cy="12" r="9"/><path d="m7 12 3 3 7-7"/></>,
  crosscircle: <><circle cx="12" cy="12" r="9"/><path d="m8 8 8 8M16 8l-8 8"/></>,
  pause: <><circle cx="12" cy="12" r="9"/><path d="M9 8v8M15 8v8"/></>,
  seal: <><path d="m12 2 3 2 3 .5.5 3 2 3-2 3-.5 3-3 .5-3 2-3-2-3-.5-.5-3-2-3 2-3 .5-3L9 4z"/><path d="m7 11 3 3 7-7"/></>,
  checklist: <><path d="m3 5 2 2 3-4M11 5h10M3 12l2 2 3-4M11 12h10M3 19l2 2 3-4M11 19h10"/></>,
  percent: <><circle cx="6" cy="6" r="3"/><circle cx="18" cy="18" r="3"/><path d="m4 20 16-16"/></>,
  warning: <><path d="m12 3 10 18H2zM12 9v5M12 18h.01"/></>,
  tray: <path d="m6 4-4 11v6h20v-6L18 4zM2 15h6l2 3h4l2-3h6"/>,
  filter: <path d="M3 6h18M6 12h12M9 18h6"/>,
  chevron: <path d="m7 10 5 5 5-5"/>,
  globe: <><circle cx="12" cy="12" r="9"/><ellipse cx="12" cy="12" rx="4" ry="9"/><path d="M3 12h18"/></>,
  halfcircle: <><circle cx="12" cy="12" r="9"/><path d="M12 3a9 9 0 0 0 0 18z" fill="currentColor"/></>,
  dashedcircle: <circle cx="12" cy="12" r="9" strokeDasharray="2 3"/>,
  function: <><path d="M15 4c-4-2-5 2-6 8L7 21c-.5 2-3 2-4 1M5 9h10M16 8l5 8M21 8l-5 8"/></>,
};
const aliases: Record<string, string> = {
  'book.closed': 'book', 'book.fill': 'book', 'checkmark': 'check', 'checkmark.circle': 'checkcircle',
  'chart.bar': 'chart', 'chart.bar.fill': 'chart', 'gearshape': 'gear', 'sidebar.left': 'sidebar',
  'magnifyingglass': 'search', 'star.fill': 'star', 'speaker.wave.2.fill': 'speaker',
  'text.cursor': 'selection', 'arrow.counterclockwise': 'rotate', 'arrow.clockwise': 'rotate',
  'arrow.counterclockwise.circle.fill': 'rotate', 'square.grid.2x2': 'grid', 'bookmark.fill': 'bookmark',
  'books.vertical': 'books', 'arrow.left.arrow.right': 'arrows', 'arrow.right': 'arrow',
  'play.fill': 'play', 'questionmark': 'question', 'checkmark.circle.fill': 'checkcircle',
  'xmark.circle.fill': 'crosscircle', 'xmark': 'cross', 'pause.circle.fill': 'pause',
  'checkmark.seal.fill': 'seal', 'chart.line.uptrend.xyaxis': 'chart',
  'exclamationmark.triangle.fill': 'warning', 'line.3.horizontal.decrease.circle': 'filter',
  'line.3.horizontal.decrease': 'filter', 'globe.americas.fill': 'globe',
  'line.3.horizontal.decrease.circle.fill': 'filter', 'text.magnifyingglass': 'search',
  'circle.lefthalf.filled': 'halfcircle', 'circle.dashed': 'dashedcircle',
};

export function Icon({ name, size = 14, className = '' }: { name: string; size?: number; className?: string }) {
  return <svg className={`icon ${className}`} width={size} height={size} viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="1.8" strokeLinecap="round" strokeLinejoin="round" aria-hidden="true">{paths[aliases[name] ?? name] ?? paths.book}</svg>;
}
export function AcademicCard({ children, emphasized = false, className = '' }: { children: ReactNode; emphasized?: boolean; className?: string }) {
  return <section className={`academic-card ${emphasized ? 'is-emphasized' : ''} ${className}`}>{children}</section>;
}
export function Metric({ value, label, color = 'var(--blue)' }: { value: ReactNode; label: string; color?: string }) {
  return <div className="metric-card"><strong style={{ color }}>{value}</strong><span>{label}</span></div>;
}
export function KnowledgeAxis({ value, tint = 'var(--blue)' }: { value: number; tint?: string }) {
  const fraction = Math.min(1, Math.max(0, value));
  return <div className="knowledge-axis" style={{ '--fraction': fraction, '--axis-tint': tint } as CSSProperties} aria-label={`掌握进度 ${Math.round(fraction * 100)}%`}>
    <div className="axis-graphic"><div className="axis-track"/><div className="axis-fill"/>
      {[0, 0.5, 1].map(node => <span key={node} className={`axis-node ${fraction >= node ? 'is-reached' : ''}`} style={{ left: `${node * 100}%` }}/>) }
      <span className="axis-marker" style={{ left: `${fraction * 100}%` }}/>
    </div>
    <div className="axis-labels"><span>陌生</span><span>学习中</span><span>已掌握</span></div>
  </div>;
}
export function SelectableEnglish({ term, onSelection, className = '' }: { term: Term; onSelection: (text: string) => void; className?: string }) {
  const ref = useRef<HTMLDivElement>(null);
  useEffect(() => { onSelection(''); window.getSelection()?.removeAllRanges(); }, [term.id, onSelection]);
  useEffect(() => {
    const changed = () => {
      const selection = window.getSelection();
      const element = ref.current;
      const within = selection && element && selection.anchorNode && selection.focusNode && element.contains(selection.anchorNode) && element.contains(selection.focusNode);
      onSelection(within ? sanitizeSelection(selection.toString()) ?? '' : '');
    };
    document.addEventListener('selectionchange', changed);
    return () => document.removeEventListener('selectionchange', changed);
  }, [onSelection]);
  const contextMenu = (event: MouseEvent) => {
    const selection = window.getSelection();
    if (!selection || selection.isCollapsed || !ref.current?.contains(selection.anchorNode) || !ref.current.contains(selection.focusNode)) return;
    event.preventDefault(); void window.vocab.showSelectionMenu(selection.toString());
  };
  return <div ref={ref} lang="en" className={`selectable-english ${className}`} onContextMenu={contextMenu} title="可选择、复制或右键朗读">{mathDisplay(term.english)}</div>;
}
export function PronunciationPanel({ term, settings, audio, compact = false }: { term: Term; settings: SpeechSettings; audio: AudioController; compact?: boolean }) {
  return <section className={`pronunciation-panel ${compact ? 'is-compact' : ''}`} aria-label="英美音标">
    <div className="pronunciation-heading"><strong>英美音标</strong><span><Icon name="speaker" size={11}/>内置离线发音</span></div>
    {(['en-US', 'en-GB'] as Accent[]).map((accent, index) => {
      const isAmerican = accent === 'en-US';
      const label = isAmerican ? '美式' : '英式';
      return <div key={accent} className={`pronunciation-row ${index ? 'with-divider' : ''}`}>
        <span className={`accent-stamp ${settings.accent === accent ? 'is-default' : ''}`}>{isAmerican ? 'US' : 'UK'}</span>
        <div className="ipa-content"><div className="accent-label">{label}{settings.accent === accent && <span>默认</span>}</div><div className="ipa" lang="en">/{isAmerican ? term.pronunciation.americanIPA : term.pronunciation.britishIPA}/</div></div>
        <button type="button" className="secondary-button accent-play" onMouseDown={event => event.preventDefault()} onClick={() => void audio.play(term.speechText, accent)}><Icon name="speaker"/>{label}朗读</button>
      </div>;
    })}
    <p className={`pronunciation-status ${audio.state.kind === 'error' || audio.state.kind === 'missing' ? 'is-error' : ''}`} role="status">{audio.state.message || '内置自然语音，离线播放。'}</p>
  </section>;
}
