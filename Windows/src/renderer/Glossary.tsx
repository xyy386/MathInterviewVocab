import {
    useEffect, useMemo, useRef, useState,
    type CSSProperties, type KeyboardEvent, type PointerEvent,
} from 'react';
import {
    filterTerms, mathDisplay, sanitizeSelection, subjectsFromTerms,
    type ProgressData, type SpeechSettings, type Term,
} from '../shared/core';
import { AcademicCard, Icon, KnowledgeAxis, Metric, PronunciationPanel, SelectableEnglish } from './ui';
import type { AudioController } from './useAudio';

export interface GlossaryViewProps {
    terms: Term[];
    progress: ProgressData;
    settings: SpeechSettings;
    audio: AudioController;
    onFavorite: (id: number) => Promise<void>;
    sidebarOpen: boolean;
    sidebarWidth: number;
    onSidebarResize: (width: number) => void;
}

export function GlossaryView({
    terms, progress, settings, audio, onFavorite,
    sidebarOpen, sidebarWidth, onSidebarResize,
}: GlossaryViewProps) {
    const [query, setQuery] = useState('');
    const [subjectID, setSubjectID] = useState('');
    const [importantOnly, setImportantOnly] = useState(false);
    const [favoritesOnly, setFavoritesOnly] = useState(false);
    const [mistakesOnly, setMistakesOnly] = useState(false);
    const [selectedID, setSelectedID] = useState<number | null>(null);
    const [filterOpen, setFilterOpen] = useState(false);
    const menuRef = useRef<HTMLDetailsElement>(null);
    const listRef = useRef<HTMLDivElement>(null);
    const resizeCleanup = useRef<(() => void) | null>(null);
    const subjects = useMemo(() => subjectsFromTerms(terms), [terms]);
    const filtered = useMemo(() => filterTerms(terms, {
        query, subjectID: subjectID || null, importantOnly, favoritesOnly, mistakesOnly,
    }, progress), [terms, query, subjectID, importantOnly, favoritesOnly, mistakesOnly, progress]);
    const selectedTerm = selectedID === null ? undefined : terms.find(term => term.id === selectedID);
    const toggleFilterCount = [importantOnly, favoritesOnly, mistakesOnly].filter(Boolean).length;
    const activeFilterCount = toggleFilterCount + (subjectID ? 1 : 0);
    const mastery = terms.length ? progress.masteredIDs.length / terms.length : 0;

    // 与 Mac 一致：筛选隐藏当前词条后回到欢迎页，不自动跳选第一条。
    useEffect(() => {
        if (selectedID !== null && !filtered.some(term => term.id === selectedID)) {
            audio.stop();
            setSelectedID(null);
        }
    }, [audio.stop, filtered, selectedID]);
    useEffect(() => () => {
        audio.stop();
        resizeCleanup.current?.();
    }, [audio.stop]);
    useEffect(() => {
        if (!filterOpen) return;
        const closeOutside = (event: globalThis.PointerEvent) => {
            if (event.target instanceof Node && !menuRef.current?.contains(event.target)) setFilterOpen(false);
        };
        const closeOnEscape = (event: globalThis.KeyboardEvent) => {
            if (event.key === 'Escape') {
                setFilterOpen(false);
                menuRef.current?.querySelector('summary')?.focus();
            }
        };
        window.addEventListener('pointerdown', closeOutside);
        window.addEventListener('keydown', closeOnEscape);
        return () => {
            window.removeEventListener('pointerdown', closeOutside);
            window.removeEventListener('keydown', closeOnEscape);
        };
    }, [filterOpen]);

    const clearFilters = () => {
        setSubjectID('');
        setImportantOnly(false);
        setFavoritesOnly(false);
        setMistakesOnly(false);
        setFilterOpen(false);
    };
    const closeFilter = () => {
        setFilterOpen(false);
        menuRef.current?.querySelector('summary')?.focus();
    };
    const selectTerm = (id: number) => {
        if (id !== selectedID) audio.stop();
        setSelectedID(id);
    };
    const navigateList = (event: KeyboardEvent<HTMLDivElement>) => {
        if (event.altKey || event.ctrlKey || event.metaKey || !filtered.length) return;
        const index = filtered.findIndex(term => term.id === selectedID);
        let nextIndex: number;
        if (event.key === 'ArrowDown') nextIndex = Math.min(index + 1, filtered.length - 1);
        else if (event.key === 'ArrowUp') nextIndex = index < 0 ? filtered.length - 1 : Math.max(index - 1, 0);
        else if (event.key === 'Home') nextIndex = 0;
        else if (event.key === 'End') nextIndex = filtered.length - 1;
        else return;
        event.preventDefault();
        const term = filtered[nextIndex];
        selectTerm(term.id);
        const row = listRef.current?.querySelector<HTMLButtonElement>(`[data-term-id="${term.id}"]`);
        row?.focus({ preventScroll: true });
        row?.scrollIntoView({ block: 'nearest' });
    };
    const startResize = (event: PointerEvent<HTMLDivElement>) => {
        if (event.button !== 0) return;
        event.preventDefault();
        resizeCleanup.current?.();
        const startX = event.clientX;
        const startWidth = sidebarWidth;
        const target = event.currentTarget;
        const pointerID = event.pointerId;
        target.setPointerCapture(pointerID);
        const move = (next: globalThis.PointerEvent) => {
            onSidebarResize(Math.max(320, Math.min(460, startWidth + next.clientX - startX)));
        };
        const cleanup = () => {
            window.removeEventListener('pointermove', move);
            window.removeEventListener('pointerup', cleanup);
            window.removeEventListener('pointercancel', cleanup);
            if (target.hasPointerCapture(pointerID)) target.releasePointerCapture(pointerID);
            resizeCleanup.current = null;
        };
        resizeCleanup.current = cleanup;
        window.addEventListener('pointermove', move);
        window.addEventListener('pointerup', cleanup);
        window.addEventListener('pointercancel', cleanup);
    };

    return (
        <div
            className={`glossary-layout${sidebarOpen ? '' : ' sidebar-collapsed'}`}
            style={{ '--sidebar-width': `${sidebarWidth}px` } as CSSProperties}
            data-testid="glossary-layout"
        >
            {sidebarOpen && (
                <aside className="term-sidebar" aria-label="专业词汇" data-testid="term-sidebar">
                    <div className="sidebar-controls">
                        <div className="search-field">
                            <Icon name="magnifyingglass" />
                            <input
                                type="text"
                                role="searchbox"
                                aria-label="搜索中文、英文或释义"
                                placeholder="搜索中文、英文或释义"
                                value={query}
                                onChange={event => setQuery(event.target.value)}
                                data-testid="term-search"
                            />
                            {query && (
                                <button type="button" className="search-clear" aria-label="清除搜索" title="清除搜索" onClick={() => setQuery('')}>
                                    <Icon name="xmark.circle.fill" />
                                </button>
                            )}
                        </div>
                        <div className="filter-row">
                            <select aria-label="科目" value={subjectID} onChange={event => setSubjectID(event.target.value)} data-testid="subject-filter">
                                <option value="">全部科目（{terms.length}）</option>
                                {subjects.map(subject => <option key={subject.id} value={subject.id}>{subject.name}（{subject.count}）</option>)}
                            </select>
                            <details ref={menuRef} className="filter-menu" open={filterOpen}>
                                <summary
                                    aria-label={toggleFilterCount ? `筛选 ${toggleFilterCount}` : '筛选'}
                                    onClick={event => { event.preventDefault(); setFilterOpen(open => !open); }}
                                >
                                    <Icon name={toggleFilterCount ? 'line.3.horizontal.decrease.circle.fill' : 'line.3.horizontal.decrease'} />
                                    <span>{toggleFilterCount ? `筛选 ${toggleFilterCount}` : '筛选'}</span>
                                    <Icon name="chevron" size={12} />
                                </summary>
                                <div className="filter-panel" role="menu" aria-label="筛选词汇">
                                    <FilterCheck label="仅看重点" checked={importantOnly} onChange={value => { setImportantOnly(value); closeFilter(); }} />
                                    <FilterCheck label="仅看收藏" checked={favoritesOnly} onChange={value => { setFavoritesOnly(value); closeFilter(); }} />
                                    <FilterCheck label="仅看错题" checked={mistakesOnly} onChange={value => { setMistakesOnly(value); closeFilter(); }} />
                                    <hr />
                                    <button type="button" role="menuitem" disabled={!activeFilterCount} onClick={() => { clearFilters(); closeFilter(); }}>清除筛选</button>
                                </div>
                            </details>
                        </div>
                        <p className="result-count">显示 {filtered.length} / {terms.length} 个词汇</p>
                    </div>

                    <div ref={listRef} className="term-list" role="listbox" aria-label="词汇列表" tabIndex={selectedID === null ? 0 : -1} onKeyDown={navigateList} data-testid="term-list">
                        <AcademicCard className="sidebar-knowledge">
                            <div className="sidebar-knowledge-heading">
                                <div><h2>知识坐标</h2><p className="caption muted">已掌握 {progress.masteredIDs.length} 个词汇</p></div>
                                <strong>{Math.trunc(mastery * 100)}%</strong>
                            </div>
                            <KnowledgeAxis value={mastery} />
                        </AcademicCard>
                        {filtered.length ? subjects.map(subject => {
                            const subjectTerms = filtered.filter(term => term.subjectID === subject.id);
                            return subjectTerms.length ? (
                                <section className="list-section" role="group" key={subject.id} aria-label={subject.name}>
                                    <h3 className="list-section-title">{subject.name}</h3>
                                    {subjectTerms.map(term => (
                                        <button
                                            type="button" role="option" aria-selected={selectedID === term.id}
                                            tabIndex={selectedID === term.id ? 0 : -1}
                                            className={`term-row${selectedID === term.id ? ' is-selected' : ''}${term.important ? ' is-important' : ''}`}
                                            key={term.id} onClick={() => selectTerm(term.id)}
                                            data-term-id={term.id} data-testid={`term-row-${term.id}`}
                                        >
                                            <span className="term-id">{term.id}</span>
                                            <span className="term-labels">
                                                <span className="term-chinese">{term.chinese}{term.important && <span className="term-important">重点</span>}</span>
                                                <span className="term-english" lang="en">{mathDisplay(term.english)}</span>
                                            </span>
                                            <span className="row-status">
                                                {progress.favoriteIDs.includes(term.id) && <span className="favorite-status" role="img" aria-label="已收藏"><Icon name="star.fill" /></span>}
                                                {progress.masteredIDs.includes(term.id)
                                                    ? <span className="mastered-status" role="img" aria-label="已掌握"><Icon name="checkmark.seal.fill" /></span>
                                                    : progress.wrongIDs.includes(term.id) && <span className="wrong-status" role="img" aria-label="待复习"><Icon name="arrow.counterclockwise.circle.fill" /></span>}
                                            </span>
                                        </button>
                                    ))}
                                </section>
                            ) : null;
                        }) : (
                            <div className="empty-sidebar">
                                <Icon name="text.magnifyingglass" size={34} />
                                <h2>没有符合条件的词汇</h2>
                                <p>请更换搜索内容、科目或筛选条件。</p>
                            </div>
                        )}
                    </div>
                </aside>
            )}
            {sidebarOpen && (
                <div className="sidebar-resizer" role="separator" aria-label="调整侧栏宽度" aria-orientation="vertical"
                    aria-valuenow={Math.round(sidebarWidth)} aria-valuemin={320} aria-valuemax={460} tabIndex={0}
                    onPointerDown={startResize}
                    onKeyDown={event => {
                        if (event.key !== 'ArrowLeft' && event.key !== 'ArrowRight') return;
                        event.preventDefault();
                        onSidebarResize(Math.max(320, Math.min(460, sidebarWidth + (event.key === 'ArrowRight' ? 10 : -10))));
                    }} />
            )}
            <main className="detail-pane">
                {selectedTerm
                    ? <TermDetail key={selectedTerm.id} term={selectedTerm} progress={progress} settings={settings} audio={audio} onFavorite={onFavorite} />
                    : <GlossaryWelcome terms={terms} progress={progress} />}
            </main>
        </div>
    );
}

function FilterCheck({ label, checked, onChange }: { label: string; checked: boolean; onChange: (value: boolean) => void }) {
    return (
        <label className="filter-check" role="menuitemcheckbox" aria-checked={checked}>
            <input type="checkbox" checked={checked} onChange={event => onChange(event.target.checked)} />
            <span>{label}</span>
        </label>
    );
}

function GlossaryWelcome({ terms, progress }: { terms: Term[]; progress: ProgressData }) {
    const subjects = useMemo(() => subjectsFromTerms(terms), [terms]);
    return (
        <div className="content-column glossary-welcome" data-testid="glossary-welcome">
            <header className="welcome-title-group">
                <h1>数学专业面试</h1>
                <h2>英文专业词汇汇总</h2>
                <p className="muted">从左侧选择一个词汇，查看规范英文、中文释义并播放发音。</p>
            </header>
            <div className="welcome-metrics">
                <Metric value={String(terms.length)} label="全部词汇" color="var(--blue)" />
                <Metric value={String(terms.filter(term => term.important).length)} label="重点词汇" color="var(--blue)" />
                <Metric value={String(progress.masteredIDs.length)} label="已掌握" color="var(--blue)" />
            </div>
            <AcademicCard className="welcome-subjects">
                <h2>五个学习板块</h2>
                {subjects.map(subject => (
                    <div className="welcome-subject-row" key={subject.id}>
                        <span className="section-badge">{subject.name}</span>
                        <span className="muted">{subject.count} 词</span>
                    </div>
                ))}
            </AcademicCard>
        </div>
    );
}

function TermDetail({ term, progress, settings, audio, onFavorite }: {
    term: Term; progress: ProgressData; settings: SpeechSettings; audio: AudioController;
    onFavorite: (id: number) => Promise<void>;
}) {
    const [selectedText, setSelectedText] = useState('');
    const [favoriteError, setFavoriteError] = useState('');
    const streak = Math.min(progress.correctStreak[term.id] ?? 0, 3);
    const favorite = progress.favoriteIDs.includes(term.id);
    const status = progress.masteredIDs.includes(term.id)
        ? { label: '已掌握', icon: 'checkmark.seal.fill', tint: 'var(--green)' }
        : progress.wrongIDs.includes(term.id)
            ? { label: '待复习', icon: 'arrow.counterclockwise.circle.fill', tint: 'var(--red)' }
            : streak > 0
                ? { label: '学习中', icon: 'circle.lefthalf.filled', tint: 'var(--blue)' }
                : { label: '未检测', icon: 'circle.dashed', tint: 'rgba(20,33,61,.62)' };
    const selection = sanitizeSelection(selectedText);

    useEffect(() => () => audio.stop(), [audio.stop]);

    return (
        <article className="content-column term-detail" data-testid="term-detail">
            <header className="detail-badges">
                <span className="section-badge"><Icon name="function" />{term.subjectName}</span>
                {term.important && <span className="section-badge important"><Icon name="bookmark.fill" />重点词汇</span>}
                <span className="term-reference">#{term.id}</span>
            </header>

            <AcademicCard emphasized={term.important} className="term-main-card">
                <h1 className="term-title">{term.chinese}</h1>
                <SelectableEnglish term={term} onSelection={setSelectedText} className="detail-english" />
                <p className="term-hint caption muted">双击选择单词，或拖动选择词组；下方可分别试听英美发音并对照音标。</p>
                <PronunciationPanel term={term} settings={settings} audio={audio} />
                <div className="speech-actions">
                    <button type="button" className="primary-button button-large" data-testid="speak-full"
                        onMouseDown={event => event.preventDefault()} onClick={() => void audio.play(term.speechText)}>
                        <Icon name="speaker.wave.2.fill" />朗读完整词组
                    </button>
                    <button type="button" className="secondary-button button-large" data-testid="speak-selection"
                        title={selection ? `朗读：${selectedText}` : '先双击单词或拖动选择一段英文'}
                        disabled={!selection} onMouseDown={event => event.preventDefault()}
                        onClick={() => { if (selection) void audio.play(selection); }}>
                        <Icon name="text.cursor" />朗读所选
                    </button>
                    <button type="button" className="secondary-button button-large" data-testid="favorite-button"
                        onClick={async () => {
                            try { await onFavorite(term.id); setFavoriteError(''); }
                            catch (error) { setFavoriteError(error instanceof Error ? error.message : '收藏状态保存失败。'); }
                        }}>
                        <Icon name={favorite ? 'star.fill' : 'star'} />{favorite ? '取消收藏' : '收藏'}
                    </button>
                </div>
                {favoriteError && <p className="persistence-error" role="alert">{favoriteError}</p>}
            </AcademicCard>

            <AcademicCard className="definition-card">
                <h2>中文释义</h2>
                <p className="definition-text">{mathDisplay(term.definition)}</p>
            </AcademicCard>

            <AcademicCard className="term-progress-card">
                <div className="term-progress-heading">
                    <h2 style={{ color: status.tint }}><Icon name={status.icon} />{status.label}</h2>
                    <span className="term-streak caption muted" data-testid="term-streak">连续答对 {streak} / 3</span>
                </div>
                <KnowledgeAxis value={streak / 3} tint={status.tint} />
                <p className="caption muted">连续答对 3 次后标记为已掌握；答错会自动加入错题复习。</p>
            </AcademicCard>
        </article>
    );
}
