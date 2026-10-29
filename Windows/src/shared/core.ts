export type Accent = 'en-US' | 'en-GB';
export type QuizDirection = 'zhToEn' | 'enToZh' | 'mixed';
export type QuizScope = 'important' | 'all' | 'subject' | 'mistakes' | 'favorites';
export type QuizLength = 'ten' | 'twenty' | 'fifty' | 'all';
export interface SpeechSettings { accent: Accent; speed: number }
export interface Term {
    id: number; subjectID: string; subjectName: string; subjectNameEn: string;
    chinese: string; english: string; definition: string; important: boolean; speechText: string;
    pronunciation: { americanIPA: string; britishIPA: string };
}
export interface QuizPreferences {
    direction: QuizDirection; scope: QuizScope; questionLength: QuizLength;
    selectedSubjectID: string; includeMastered: boolean;
}
export interface QuizAttempt {
    id: string; termID: number; direction: 'zhToEn' | 'enToZh';
    selectedOptionID: number; correctOptionID: number; answeredAt: string;
}
export interface ProgressData {
    attempts: QuizAttempt[]; favoriteIDs: number[]; correctStreak: Record<string, number>;
    wrongIDs: number[]; masteredIDs: number[]; quizPreferences: QuizPreferences;
}
export interface QuizQuestion {
    id: string; term: Term; direction: 'zhToEn' | 'enToZh'; options: Term[]; correctOptionID: number;
}
export interface SubjectSummary { id: string; name: string; englishName: string; count: number }
export interface TermFilters {
    query: string; subjectID?: string | null; importantOnly: boolean; favoritesOnly: boolean; mistakesOnly: boolean;
}
const subjectOrder = ['subjects', 'analysis', 'algebra', 'probability', 'statistics'];

export function defaultPreferences(): QuizPreferences {
    return { direction: 'mixed', scope: 'important', questionLength: 'twenty', selectedSubjectID: 'analysis', includeMastered: true };
}
export function defaultProgress(): ProgressData {
    return { attempts: [], favoriteIDs: [], correctStreak: {}, wrongIDs: [], masteredIDs: [], quizPreferences: defaultPreferences() };
}
function object(input: unknown): Record<string, unknown> {
    if (!input || typeof input !== 'object' || Array.isArray(input)) throw new Error('数据必须是 JSON 对象。');
    return input as Record<string, unknown>;
}
function enumValue<T extends string>(input: unknown, values: readonly T[], fallback: T): T {
    if (input === undefined) return fallback;
    if (typeof input !== 'string' || !values.includes(input as T)) throw new Error(`无效枚举值：${String(input)}`);
    return input as T;
}
function integer(input: unknown): number {
    if (typeof input !== 'number' || !Number.isSafeInteger(input) || input < 1) throw new Error('词汇编号必须为正整数。');
    return input;
}
function boolean(input: unknown, fallback: boolean): boolean {
    if (input === undefined) return fallback;
    if (typeof input !== 'boolean') throw new Error('开关值必须为布尔值。');
    return input;
}
function ids(input: unknown): number[] {
    if (input === undefined) return [];
    if (!Array.isArray(input)) throw new Error('编号集合必须为数组。');
    return [...new Set(input.map(integer))];
}

export function validatePreferences(input: unknown): QuizPreferences {
    const value = object(input);
    const defaults = defaultPreferences();
    let legacySubjects: string[] = [];
    if (value.selectedSubjectIDs !== undefined) {
        if (!Array.isArray(value.selectedSubjectIDs) || value.selectedSubjectIDs.some(id => typeof id !== 'string' || !subjectOrder.includes(id))) {
            throw new Error('无效旧版科目列表。');
        }
        legacySubjects = [...new Set(value.selectedSubjectIDs as string[])].sort();
    }
    const selectedSubjectID = value.selectedSubjectID ?? legacySubjects[0] ?? defaults.selectedSubjectID;
    if (typeof selectedSubjectID !== 'string' || !subjectOrder.includes(selectedSubjectID)) throw new Error('无效科目。');
    const legacyImportant = boolean(value.importantOnly, true);
    const fallbackScope: QuizScope = legacySubjects.length ? 'subject' : legacyImportant ? 'important' : 'all';
    if (value.questionCount !== undefined && (typeof value.questionCount !== 'number' || !Number.isSafeInteger(value.questionCount))) {
        throw new Error('无效旧版题量。');
    }
    const fallbackLength: QuizLength = value.questionCount === 10 ? 'ten' : value.questionCount === 50 ? 'fifty' : value.questionCount === 326 ? 'all' : 'twenty';
    return {
        direction: enumValue(value.direction, ['zhToEn', 'enToZh', 'mixed'], defaults.direction),
        scope: enumValue(value.scope, ['important', 'all', 'subject', 'mistakes', 'favorites'], fallbackScope),
        questionLength: enumValue(value.questionLength, ['ten', 'twenty', 'fifty', 'all'], fallbackLength),
        selectedSubjectID, includeMastered: boolean(value.includeMastered, defaults.includeMastered),
    };
}
export function validateSettings(input: unknown): SpeechSettings {
    const value = object(input);
    const speed = value.speed ?? value.speechSpeed ?? 1;
    if (typeof speed !== 'number' || ![0.75, 1, 1.25].includes(speed)) throw new Error('无效语速。');
    return { accent: enumValue(value.accent ?? value.voiceLocale, ['en-US', 'en-GB'], 'en-US'), speed };
}
export function decodeProgress(input: unknown): ProgressData {
    const value = object(input);
    if (value.attempts !== undefined && !Array.isArray(value.attempts)) throw new Error('答题历史必须为数组。');
    const attempts = ((value.attempts ?? []) as unknown[]).map(inputAttempt => {
        const a = object(inputAttempt);
        if (typeof a.id !== 'string' || !/^[\da-f]{8}-(?:[\da-f]{4}-){3}[\da-f]{12}$/i.test(a.id)) throw new Error('无效答题 UUID。');
        if (typeof a.answeredAt !== 'string' || !/^\d{4}-\d\d-\d\dT/.test(a.answeredAt) || !Number.isFinite(Date.parse(a.answeredAt))) {
            throw new Error('无效答题时间。');
        }
        const direction = enumValue(a.direction, ['zhToEn', 'enToZh'], 'zhToEn');
        const result: QuizAttempt = { id: a.id, termID: integer(a.termID), direction,
            selectedOptionID: integer(a.selectedOptionID), correctOptionID: integer(a.correctOptionID), answeredAt: a.answeredAt };
        if (result.termID !== result.correctOptionID) throw new Error('正确答案编号与题目不一致。');
        return result;
    });
    if (new Set(attempts.map(a => a.id)).size !== attempts.length) throw new Error('答题记录编号重复。');
    const correctStreak: Record<string, number> = {};
    if (value.correctStreak !== undefined) {
        for (const [key, count] of Object.entries(object(value.correctStreak))) {
            if (!/^[1-9]\d*$/.test(key) || typeof count !== 'number' || !Number.isSafeInteger(count) || count < 0) {
                throw new Error('无效连续答对计数。');
            }
            correctStreak[key] = count;
        }
    }
    return { attempts, correctStreak, favoriteIDs: ids(value.favoriteIDs), wrongIDs: ids(value.wrongIDs),
        masteredIDs: ids(value.masteredIDs), quizPreferences: value.quizPreferences === undefined ? defaultPreferences() : validatePreferences(value.quizPreferences) };
}

export function filterTerms(terms: Term[], filters: TermFilters, progress: ProgressData): Term[] {
    const query = filters.query.trim().toLocaleLowerCase();
    return terms.filter(term => (!filters.subjectID || term.subjectID === filters.subjectID)
        && (!filters.importantOnly || term.important)
        && (!filters.favoritesOnly || progress.favoriteIDs.includes(term.id))
        && (!filters.mistakesOnly || progress.wrongIDs.includes(term.id))
        && (!query || [term.chinese, term.english, term.definition].some(text => text.toLocaleLowerCase().includes(query))));
}
export function makePool(terms: Term[], preferences: QuizPreferences, progress: ProgressData): Term[] {
    return terms.filter(term => {
        const scopeMatch = preferences.scope === 'all' || (preferences.scope === 'important' && term.important)
            || (preferences.scope === 'subject' && term.subjectID === preferences.selectedSubjectID)
            || (preferences.scope === 'mistakes' && progress.wrongIDs.includes(term.id))
            || (preferences.scope === 'favorites' && progress.favoriteIDs.includes(term.id));
        return scopeMatch && (preferences.includeMastered || !progress.masteredIDs.includes(term.id));
    });
}
export function resolveQuestionCount(length: QuizLength, available: number): number {
    return Math.max(0, Math.min(({ ten: 10, twenty: 20, fifty: 50, all: available })[length], available));
}
function seededRandom(seed: number): () => number {
    let state = seed >>> 0;
    return () => { state = (state + 0x6D2B79F5) >>> 0; let value = state;
        value = Math.imul(value ^ (value >>> 15), value | 1);
        value ^= value + Math.imul(value ^ (value >>> 7), value | 61);
        return ((value ^ (value >>> 14)) >>> 0) / 4294967296; };
}
function shuffled<T>(values: T[], random: () => number): T[] {
    const result = [...values];
    for (let i = result.length - 1; i > 0; i--) { const j = Math.floor(random() * (i + 1)); [result[i], result[j]] = [result[j], result[i]]; }
    return result;
}
export function makeQuestions(pool: Term[], allTerms: Term[], count: number, requestedDirection: QuizDirection, seed?: number): QuizQuestion[] {
    if (!Number.isInteger(count) || count <= 0) return [];
    const random = seed === undefined ? Math.random : seededRandom(seed);
    const unique = (terms: Term[]) => [...new Map(terms.map(term => [term.id, term])).values()];
    const distractorTerms = unique(allTerms);
    const questionTerms = shuffled(unique(pool).filter(t => distractorTerms.filter(d => d.id !== t.id).length >= 3), random);
    return questionTerms.slice(0, count).map((term, index) => {
        const direction = requestedDirection === 'mixed' ? random() < 0.5 ? 'zhToEn' : 'enToZh' : requestedDirection;
        const candidates = [
            ...shuffled(distractorTerms.filter(d => d.id !== term.id && d.subjectID === term.subjectID), random),
            ...shuffled(distractorTerms.filter(d => d.id !== term.id && d.subjectID !== term.subjectID), random),
        ];
        const label = (t: Term) => (direction === 'zhToEn' ? t.english : t.chinese).trim().toLocaleLowerCase();
        const labels = new Set([label(term)]);
        const selected: Term[] = [];
        for (const candidate of candidates) {
            if (selected.length === 3) break;
            if (labels.has(label(candidate))) continue;
            labels.add(label(candidate)); selected.push(candidate);
        }
        for (const candidate of candidates) {
            if (selected.length === 3) break;
            if (!selected.some(t => t.id === candidate.id)) selected.push(candidate);
        }
        return { id: `${term.id}-${direction}-${index}`, term, direction, options: shuffled([term, ...selected], random), correctOptionID: term.id };
    });
}
export function recordAttempt(progress: ProgressData, attempt: QuizAttempt): ProgressData {
    const correct = attempt.selectedOptionID === attempt.correctOptionID;
    const streak = correct ? Math.min(Number.MAX_SAFE_INTEGER, (progress.correctStreak[attempt.termID] ?? 0) + 1) : 0;
    const wrongIDs = new Set(progress.wrongIDs), masteredIDs = new Set(progress.masteredIDs);
    if (!correct) { wrongIDs.add(attempt.termID); masteredIDs.delete(attempt.termID); }
    else { if (streak >= 2) wrongIDs.delete(attempt.termID); if (streak >= 3) masteredIDs.add(attempt.termID); }
    return { ...progress, attempts: [...progress.attempts, attempt], correctStreak: { ...progress.correctStreak, [attempt.termID]: streak },
        wrongIDs: [...wrongIDs], masteredIDs: [...masteredIDs] };
}
export function toggleFavorite(progress: ProgressData, id: number): ProgressData {
    const favorites = new Set(progress.favoriteIDs);
    if (!favorites.delete(id)) favorites.add(id);
    return { ...progress, favoriteIDs: [...favorites] };
}
export function subjectsFromTerms(terms: Term[]): SubjectSummary[] {
    return subjectOrder.map(id => {
        const matching = terms.filter(t => t.subjectID === id), first = matching[0];
        return { id, name: first?.subjectName ?? id, englishName: first?.subjectNameEn ?? id, count: matching.length };
    }).filter(subject => subject.count > 0);
}
export function summarizeProgress(terms: Term[], progress: ProgressData) {
    const correctAttemptCount = progress.attempts.filter(a => a.selectedOptionID === a.correctOptionID).length;
    return { attemptedCount: new Set(progress.attempts.map(a => a.termID)).size, masteredCount: progress.masteredIDs.length,
        wrongCount: progress.wrongIDs.length, correctAttemptCount, totalAttemptCount: progress.attempts.length,
        accuracyPercentage: progress.attempts.length ? Math.round(correctAttemptCount / progress.attempts.length * 100) : 0,
        overallMastery: terms.length ? progress.masteredIDs.length / terms.length : 0,
        subjects: subjectsFromTerms(terms).map(subject => {
            const masteredCount = terms.filter(t => t.subjectID === subject.id && progress.masteredIDs.includes(t.id)).length;
            return { ...subject, masteredCount, fraction: subject.count ? masteredCount / subject.count : 0 };
        }), wrongTerms: terms.filter(t => progress.wrongIDs.includes(t.id)) };
}

export function sanitizeSelection(selection: string): string | null {
    const value = selection.replace(/^[\p{P}\s]+|[\p{P}\s]+$/gu, '');
    return value && /[\p{L}\p{N}]/u.test(value) ? value : null;
}
export function normalizeAudioText(text: string): string {
    return text.normalize('NFKC').replace(/[‘’ʼ＇]/g, "'").replace(/[‐‑‒–—−]/g, '-').replace(/\s+/gu, ' ').trim();
}
export function mathDisplay(source: string): string {
    let text = source.replaceAll('`', '').replaceAll('\\(', '').replaceAll('\\)', '').replaceAll('\\[', '').replaceAll('\\]', '');
    const replacements: [string, string][] = [
        ['\\operatorname{adj}', 'adj'], ['^\\mathsf{T}', 'ᵀ'], ['\\mathsf{T}', 'ᵀ'], ['\\mathrm{d}', 'd'], ['\\mathcal F', '𝓕'],
        ['\\varepsilon', 'ε'], ['\\epsilon', 'ε'], ['\\lambda', 'λ'], ['\\delta', 'δ'], ['\\xi', 'ξ'], ['\\alpha', 'α'],
        ['\\Omega', 'Ω'], ['\\sigma', 'σ'], ['\\infty', '∞'], ['\\times', '×'], ['\\le', '≤'], ['\\ge', '≥'], ['\\mid', '∣'],
        ['\\cap', '∩'], ['\\oplus', '⊕'], ['\\perp', '⊥'], ['\\subseteq', '⊆'], ['\\det', 'det'], ['\\dim', 'dim'],
        ['\\sum', '∑'], ['\\int', '∫'], ['\\in', '∈'], ['\\ldots', '…'], ['\\,', ' '], ['\\!', ''], ['^2', '²'], ['^3', '³'], ['^{-1}', '⁻¹'],
        ['\\{', '{'], ['\\}', '}'], ['\\', ''],
    ];
    for (const [from, to] of replacements) text = text.replaceAll(from, to);
    return text;
}
