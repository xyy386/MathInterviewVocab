import { useCallback, useEffect, useMemo, useRef, useState } from 'react';
import {
    makePool, makeQuestions, mathDisplay, resolveQuestionCount, sanitizeSelection,
    subjectsFromTerms, summarizeProgress,
    type ProgressData, type QuizAttempt, type QuizDirection, type QuizLength,
    type QuizPreferences, type QuizQuestion, type QuizScope, type SpeechSettings, type Term,
} from '../shared/core';
import { AcademicCard, Icon, KnowledgeAxis, Metric, PronunciationPanel, SelectableEnglish } from './ui';
import type { AudioController } from './useAudio';

interface LearningProps {
    terms: Term[];
    progress: ProgressData;
    audio: AudioController;
}

interface QuizProps extends LearningProps {
    settings: SpeechSettings;
    onPreferences: (preferences: QuizPreferences) => Promise<void>;
    onAnswer: (attempt: QuizAttempt) => Promise<void>;
}

const directions: { value: QuizDirection; title: string }[] = [
    { value: 'zhToEn', title: '中文 → 英文' },
    { value: 'enToZh', title: '英文 → 中文' },
    { value: 'mixed', title: '双向混合' },
];

const scopes: { value: QuizScope; title: string; icon: string }[] = [
    { value: 'important', title: '重点词汇', icon: 'bookmark.fill' },
    { value: 'all', title: '全部词汇', icon: 'square.grid.2x2' },
    { value: 'subject', title: '指定科目', icon: 'books.vertical' },
    { value: 'mistakes', title: '错题复习', icon: 'arrow.counterclockwise' },
    { value: 'favorites', title: '我的收藏', icon: 'star.fill' },
];

const lengths: { value: QuizLength; title: string }[] = [
    { value: 'ten', title: '10 题' }, { value: 'twenty', title: '20 题' },
    { value: 'fifty', title: '50 题' }, { value: 'all', title: '全部' },
];

function emptyScopeMessage(scope: QuizScope): string {
    if (scope === 'mistakes') return '答错的词汇会自动出现在这里。';
    if (scope === 'favorites') return '先在词汇详情中收藏想要练习的术语。';
    return '请开启“包含已经掌握的词汇”或选择其他范围。';
}

function editableTarget(target: EventTarget | null): boolean {
    return target instanceof HTMLElement
        && (target.isContentEditable || ['INPUT', 'TEXTAREA', 'SELECT'].includes(target.tagName));
}

function messageFrom(error: unknown, fallback: string): string {
    return error instanceof Error ? error.message : fallback;
}

function SettingHeading({ title, icon }: { title: string; icon: string }) {
    return <h2 className="setting-heading"><Icon name={icon} size={13} /><span>{title}</span></h2>;
}

function EmptyState({ title, message, icon = 'tray' }: { title: string; message: string; icon?: string }) {
    return (
        <AcademicCard className="learning-empty">
            <Icon name={icon} size={36} />
            <h2>{title}</h2>
            <p>{message}</p>
        </AcademicCard>
    );
}

export function QuizView({ terms, progress, settings, audio, onPreferences, onAnswer }: QuizProps) {
    const subjects = useMemo(() => subjectsFromTerms(terms), [terms]);
    const [stage, setStage] = useState<'setup' | 'active' | 'summary'>('setup');
    const [preferences, setPreferences] = useState<QuizPreferences>(() => progress.quizPreferences);
    const [questions, setQuestions] = useState<QuizQuestion[]>([]);
    const [questionIndex, setQuestionIndex] = useState(0);
    const [selectedOptionID, setSelectedOptionID] = useState<number | null>(null);
    const [feedbackSelection, setFeedbackSelection] = useState('');
    const [answeredCount, setAnsweredCount] = useState(0);
    const [correctCount, setCorrectCount] = useState(0);
    const [saving, setSaving] = useState(false);
    const [starting, setStarting] = useState(false);
    const [error, setError] = useState('');
    const answerLock = useRef(false);
    const startLock = useRef(false);

    const pool = useMemo(() => makePool(terms, preferences, progress), [terms, preferences, progress]);
    const requestedCount = resolveQuestionCount(preferences.questionLength, pool.length);
    const currentQuestion = questions[questionIndex];

    const startQuiz = useCallback(async (override: Partial<QuizPreferences> = {}) => {
        if (startLock.current || saving) return;
        const nextPreferences = { ...preferences, ...override };
        const nextPool = makePool(terms, nextPreferences, progress);
        const nextQuestions = makeQuestions(nextPool, terms,
            resolveQuestionCount(nextPreferences.questionLength, nextPool.length), nextPreferences.direction);
        if (nextQuestions.length === 0) {
            setStage('setup');
            return;
        }

        startLock.current = true;
        setStarting(true);
        setError('');
        try {
            await onPreferences(nextPreferences);
            audio.stop();
            window.getSelection()?.removeAllRanges();
            setPreferences(nextPreferences);
            setQuestions(nextQuestions);
            setQuestionIndex(0);
            setSelectedOptionID(null);
            setFeedbackSelection('');
            setAnsweredCount(0);
            setCorrectCount(0);
            answerLock.current = false;
            setStage('active');
        } catch (failure) {
            setError(messageFrom(failure, '本地记录保存失败。'));
        } finally {
            startLock.current = false;
            setStarting(false);
        }
    }, [audio.stop, onPreferences, preferences, progress, saving, terms]);

    const selectAnswer = useCallback(async (optionID: number) => {
        const question = questions[questionIndex];
        if (stage !== 'active' || !question || selectedOptionID !== null || answerLock.current || saving) return;
        if (!question.options.some(option => option.id === optionID)) return;
        // 先同步锁定，再提交；快速双击或按键也只能记录一次。
        answerLock.current = true;
        setSelectedOptionID(optionID);
        setSaving(true);
        setAnsweredCount(count => count + 1);
        if (optionID === question.correctOptionID) setCorrectCount(count => count + 1);
        const attempt: QuizAttempt = {
            id: crypto.randomUUID(), termID: question.term.id, direction: question.direction,
            selectedOptionID: optionID, correctOptionID: question.correctOptionID, answeredAt: new Date().toISOString(),
        };
        try {
            await onAnswer(attempt);
        } catch (failure) {
            setError(messageFrom(failure, '本地记录保存失败。'));
        } finally {
            setSaving(false);
        }
    }, [onAnswer, questionIndex, questions, saving, selectedOptionID, stage]);

    const advance = useCallback(() => {
        if (selectedOptionID === null || saving) return;
        audio.stop();
        setFeedbackSelection('');
        window.getSelection()?.removeAllRanges();
        if (questionIndex + 1 === questions.length) {
            setStage('summary');
            return;
        }
        answerLock.current = false;
        setQuestionIndex(index => index + 1);
        setSelectedOptionID(null);
    }, [audio.stop, questionIndex, questions.length, saving, selectedOptionID]);

    useEffect(() => {
        const onKeyDown = (event: KeyboardEvent) => {
            if (stage !== 'active' || event.repeat || event.ctrlKey || event.metaKey || event.altKey || event.shiftKey
                || editableTarget(event.target) || document.querySelector('[role="dialog"]')) return;
            if (selectedOptionID === null && /^[1-4]$/.test(event.key)) {
                const option = currentQuestion?.options[Number(event.key) - 1];
                if (option) {
                    event.preventDefault();
                    void selectAnswer(option.id);
                }
            } else if (selectedOptionID !== null && event.key === 'Enter') {
                event.preventDefault();
                advance();
            }
        };
        window.addEventListener('keydown', onKeyDown);
        return () => window.removeEventListener('keydown', onKeyDown);
    }, [advance, currentQuestion, selectAnswer, selectedOptionID, stage]);

    useEffect(() => () => audio.stop(), [audio.stop]);

    const persistenceWarning = error ? (
        <AcademicCard className="persistence-error" >
            <Icon name="exclamationmark.triangle.fill" size={13} />
            <span>本地记录保存失败：{error}</span>
        </AcademicCard>
    ) : null;

    if (stage === 'setup') {
        return (
            <main className="scroll-surface learning-view" data-testid="quiz-setup">
                <div className="reading-column quiz-setup">
                    <header className="page-heading">
                        <h1>用主动选择检验记忆</h1>
                        <p>每题四个选项，答题后立即查看释义和英文发音。</p>
                    </header>

                    <AcademicCard className="quiz-setting-card direction-card">
                        <SettingHeading title="出题方向" icon="arrow.left.arrow.right" />
                        <div className="picker-row"><span>出题方向</span><div className="segmented-control three-way" role="radiogroup" aria-label="出题方向" data-testid="quiz-direction">
                            {directions.map(direction => (
                                <button type="button" role="radio" aria-checked={preferences.direction === direction.value}
                                    className={preferences.direction === direction.value ? 'is-active' : ''} key={direction.value}
                                    onClick={() => setPreferences({ ...preferences, direction: direction.value })}>
                                    {direction.title}
                                </button>
                            ))}
                        </div></div>
                    </AcademicCard>

                    <AcademicCard className="quiz-setting-card scope-card">
                        <SettingHeading title="练习范围" icon="scope" />
                        <div className="scope-grid" data-testid="quiz-scopes">
                            {scopes.map(scope => {
                                const count = makePool(terms, { ...preferences, scope: scope.value }, progress).length;
                                return (
                                    <button type="button" key={scope.value} aria-pressed={preferences.scope === scope.value}
                                        className={`scope-option${preferences.scope === scope.value ? ' is-selected' : ''}`}
                                        onClick={() => setPreferences({ ...preferences, scope: scope.value })}>
                                        <Icon name={scope.icon} size={16} className="scope-icon" />
                                        <span className="scope-copy"><strong>{scope.title}</strong><small>{count} 个词</small></span>
                                    </button>
                                );
                            })}
                        </div>
                        {preferences.scope === 'subject' && (
                            <label className="quiz-subject-picker">
                                <span>选择科目</span>
                                <select aria-label="选择科目" value={preferences.selectedSubjectID} data-testid="quiz-subject"
                                    onChange={event => setPreferences({ ...preferences, selectedSubjectID: event.target.value })}>
                                    {subjects.map(subject => (
                                        <option key={subject.id} value={subject.id}>
                                            {subject.name}（{makePool(terms, { ...preferences, scope: 'subject', selectedSubjectID: subject.id }, progress).length}）
                                        </option>
                                    ))}
                                </select>
                            </label>
                        )}
                    </AcademicCard>

                    <AcademicCard className="quiz-setting-card length-card">
                        <SettingHeading title="题目数量" icon="number" />
                        <div className="picker-row"><span>题目数量</span><div className="segmented-control four-way" role="radiogroup" aria-label="题目数量" data-testid="quiz-length">
                            {lengths.map(length => (
                                <button type="button" role="radio" aria-checked={preferences.questionLength === length.value}
                                    className={preferences.questionLength === length.value ? 'is-active' : ''} key={length.value}
                                    onClick={() => setPreferences({ ...preferences, questionLength: length.value })}>
                                    {length.title}
                                </button>
                            ))}
                        </div></div>
                        <label className="include-mastered-row">
                            <input type="checkbox" aria-label="包含已经掌握的词汇" checked={preferences.includeMastered}
                                onChange={event => setPreferences({ ...preferences, includeMastered: event.target.checked })} />
                            <span>包含已经掌握的词汇</span>
                        </label>
                    </AcademicCard>

                    {pool.length === 0 ? (
                        <EmptyState title="当前范围没有可练习的词汇" message={emptyScopeMessage(preferences.scope)} />
                    ) : (
                        <button type="button" className="primary-button start-quiz" disabled={starting}
                            onClick={() => void startQuiz()} data-testid="start-quiz">
                            <span><Icon name="play.fill" size={13} />开始检测</span><small>{requestedCount} 题</small>
                        </button>
                    )}
                    {persistenceWarning}
                </div>
            </main>
        );
    }

    if (stage === 'summary') {
        const accuracy = answeredCount ? Math.round(correctCount / answeredCount * 100) : 0;
        const result = answeredCount === 0
            ? { title: '准备好后再开始', icon: 'pause.circle.fill', color: 'var(--blue)' }
            : accuracy >= 90
                ? { title: '掌握得很扎实', icon: 'checkmark.seal.fill', color: 'var(--green)' }
                : accuracy >= 70
                    ? { title: '再复习一轮就更稳了', icon: 'chart.line.uptrend.xyaxis', color: 'var(--blue)' }
                    : { title: '从错题开始巩固', icon: 'arrow.counterclockwise.circle.fill', color: 'var(--red)' };
        return (
            <main className="scroll-surface learning-view" data-testid="quiz-summary">
                <div className="reading-column quiz-summary">
                    <div className="summary-symbol" style={{ color: result.color }}><Icon name={result.icon} size={58} /></div>
                    <header className="summary-heading">
                        <h1>{result.title}</h1>
                        <p>{answeredCount === 0 ? '本轮尚未作答' : `本轮完成 ${answeredCount} 题`}</p>
                    </header>
                    <div className="summary-metrics">
                        <Metric value={String(correctCount)} label="答对" color="var(--green)" />
                        <Metric value={String(Math.max(0, answeredCount - correctCount))} label="答错" color="var(--red)" />
                        <Metric value={`${accuracy}%`} label="正确率" color="var(--blue)" />
                    </div>
                    <div className="summary-actions">
                        <button type="button" className="primary-button" onClick={() => void startQuiz()} disabled={starting || saving} data-testid="restart-quiz">
                            <Icon name="arrow.clockwise" size={13} />再来一轮
                        </button>
                        {progress.wrongIDs.length > 0 && (
                            <button type="button" className="secondary-button" disabled={starting || saving}
                                onClick={() => void startQuiz({ scope: 'mistakes' })}>
                                <Icon name="arrow.counterclockwise" size={13} />复习当前错题（{progress.wrongIDs.length}）
                            </button>
                        )}
                        <button type="button" className="link-button" onClick={() => { audio.stop(); setStage('setup'); }}>返回设置</button>
                    </div>
                    {persistenceWarning}
                </div>
            </main>
        );
    }

    if (!currentQuestion) {
        return <EmptyState title="无法生成题目" message="请返回并更换练习范围。" icon="exclamationmark.triangle.fill" />;
    }

    const correct = selectedOptionID === currentQuestion.correctOptionID;
    return (
        <main className="scroll-surface learning-view" data-testid="quiz-active">
            <div className="reading-column quiz-active">
                <div className="quiz-status-line">
                    <span className="quiz-counter">第 {questionIndex + 1} / {questions.length} 题</span>
                    <span className="quiz-correct-count">答对 {correctCount}</span>
                    <button type="button" className="secondary-button small-button" onClick={() => { audio.stop(); setStage('summary'); }}>结束本轮</button>
                </div>
                <progress className="quiz-progress" value={answeredCount} max={Math.max(questions.length, 1)} aria-label="检测进度" />

                <AcademicCard emphasized className="quiz-question-card">
                    <span className="section-badge"><Icon name="questionmark" size={10} />{currentQuestion.direction === 'zhToEn' ? '选择对应英文' : '选择对应中文'}</span>
                    <h1 lang={currentQuestion.direction === 'zhToEn' ? 'zh-CN' : 'en'}>{currentQuestion.direction === 'zhToEn' ? currentQuestion.term.chinese : mathDisplay(currentQuestion.term.english)}</h1>
                </AcademicCard>

                <div className="answer-list" data-testid="answer-options">
                    {currentQuestion.options.map((option, index) => {
                        const isCorrect = option.id === currentQuestion.correctOptionID;
                        const isSelected = option.id === selectedOptionID;
                        const revealed = selectedOptionID !== null;
                        const state = !revealed ? '' : isCorrect ? ' is-correct' : isSelected ? ' is-wrong' : ' is-muted';
                        const label = currentQuestion.direction === 'zhToEn' ? mathDisplay(option.english) : option.chinese;
                        return (
                            <button type="button" key={option.id} className={`answer-option${state}`} disabled={revealed}
                                aria-label={`选项 ${index + 1}，${label}`} onClick={() => void selectAnswer(option.id)} data-testid={`answer-option-${index + 1}`}>
                                <span className="answer-index">{String.fromCharCode(65 + index)}</span>
                                <span className="answer-label" lang={currentQuestion.direction === 'zhToEn' ? 'en' : 'zh-CN'}>{label}</span>
                                {revealed && isCorrect && <Icon name="checkmark.circle.fill" size={15} className="answer-correct-icon" />}
                                {revealed && !isCorrect && isSelected && <Icon name="xmark.circle.fill" size={15} className="answer-wrong-icon" />}
                            </button>
                        );
                    })}
                </div>

                {selectedOptionID !== null && (
                    <AcademicCard emphasized className="answer-feedback" >
                        <div className={`feedback-result ${correct ? 'is-correct' : 'is-wrong'}`} data-testid="answer-feedback">
                            <Icon name={correct ? 'checkmark.circle.fill' : 'xmark.circle.fill'} size={13} /><strong>{correct ? '回答正确' : '回答错误'}</strong>
                        </div>
                        <h2>{currentQuestion.term.chinese}</h2>
                        <SelectableEnglish term={currentQuestion.term} onSelection={setFeedbackSelection} className="feedback-english" />
                        <PronunciationPanel term={currentQuestion.term} settings={settings} audio={audio} compact />
                        <div className="feedback-speech-actions">
                            <button type="button" className="secondary-button" onMouseDown={event => event.preventDefault()}
                                onClick={() => audio.play(currentQuestion.term.speechText)}>
                                <Icon name="speaker.wave.2.fill" size={13} />朗读完整词组
                            </button>
                            <button type="button" className="secondary-button" disabled={sanitizeSelection(feedbackSelection) === null}
                                onMouseDown={event => event.preventDefault()} onClick={() => audio.play(feedbackSelection)}>
                                <Icon name="text.cursor" size={13} />朗读所选
                            </button>
                        </div>
                        <p className="feedback-definition">{mathDisplay(currentQuestion.term.definition)}</p>
                        <button type="button" className="primary-button next-question" disabled={saving} onClick={advance} data-testid="next-question">
                            {questionIndex + 1 === questions.length ? '查看结果' : '下一题'}<Icon name="arrow.right" size={13} />
                        </button>
                    </AcademicCard>
                )}
                {persistenceWarning}
            </div>
        </main>
    );
}

export function ProgressView({ terms, progress, audio, persistenceError }: LearningProps & { persistenceError?: string | null }) {
    const summary = useMemo(() => summarizeProgress(terms, progress), [terms, progress]);
    const metrics = [
        { value: String(summary.attemptedCount), label: '已检测词汇', icon: 'checklist', color: 'var(--blue)' },
        { value: String(summary.masteredCount), label: '已掌握', icon: 'checkmark.seal.fill', color: 'var(--green)' },
        { value: String(summary.wrongCount), label: '待复习', icon: 'arrow.counterclockwise', color: 'var(--red)' },
        { value: `${summary.accuracyPercentage}%`, label: '总正确率', icon: 'percent', color: 'var(--navy)' },
    ];
    const wrongTerms = summary.wrongTerms.slice(0, 12);

    useEffect(() => () => audio.stop(), [audio.stop]);

    return (
        <main className="scroll-surface learning-view" data-testid="progress-view">
            <div className="reading-column progress-page">
                <header className="page-heading">
                    <h1>学习记录</h1>
                    <p>记录只保存在当前设备，不需要账号或网络。</p>
                </header>

                <div className="progress-metrics">
                    {metrics.map(metric => (
                        <AcademicCard className="progress-metric" key={metric.label}>
                            <span className="progress-metric-icon" style={{ color: metric.color }}><Icon name={metric.icon} size={15} /></span>
                            <strong>{metric.value}</strong>
                            <small>{metric.label}</small>
                        </AcademicCard>
                    ))}
                </div>

                <AcademicCard className="progress-overall">
                    <div className="progress-section-title"><h2>总体掌握度</h2><span>{summary.masteredCount} / {terms.length}</span></div>
                    <KnowledgeAxis value={summary.overallMastery} />
                </AcademicCard>

                <AcademicCard className="progress-subjects">
                    <h2>分科学习进度</h2>
                    {summary.subjects.map(subject => (
                        <div className="subject-progress-row" key={subject.id}>
                            <div className="subject-progress-heading">
                                <div><strong>{subject.name}</strong><small>{subject.englishName}</small></div>
                                <span>{subject.masteredCount} / {subject.count}</span>
                            </div>
                            <progress className="subject-progress-track" value={subject.fraction} max={1} aria-label={`${subject.name}掌握度`} />
                        </div>
                    ))}
                </AcademicCard>

                {progress.wrongIDs.length > 0 ? (
                    <AcademicCard className="progress-review">
                        <div className="progress-section-title"><h2>待复习错题</h2><span className="section-badge review-count">{progress.wrongIDs.length} 个</span></div>
                        {wrongTerms.map((term, index) => (
                            <div className="review-entry" key={term.id}>
                                <div className="review-row">
                                    <div><strong>{term.chinese}</strong><small>{mathDisplay(term.english)}</small></div>
                                    <button type="button" className="borderless-button" aria-label={`播放 ${term.english} 的发音`} onClick={() => audio.play(term.speechText)}>
                                        <Icon name="speaker.wave.2.fill" size={13} />
                                    </button>
                                </div>
                                {index < wrongTerms.length - 1 && <hr className="review-divider" />}
                            </div>
                        ))}
                        {summary.wrongTerms.length > 12 && <p className="review-more">另有 {summary.wrongTerms.length - 12} 个错题，可在“词汇”中选择“仅看错题”。</p>}
                    </AcademicCard>
                ) : (
                    <AcademicCard className="progress-no-mistakes">
                        <Icon name="checkmark.circle.fill" size={13} /><strong>当前没有待复习词汇</strong>
                    </AcademicCard>
                )}

                {persistenceError && (
                    <AcademicCard className="persistence-error">
                        <Icon name="exclamationmark.triangle.fill" size={10} /><span>本地记录保存失败：{persistenceError}</span>
                    </AcademicCard>
                )}
            </div>
        </main>
    );
}
