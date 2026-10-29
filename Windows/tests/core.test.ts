import test from 'node:test';
import assert from 'node:assert/strict';
import { readFileSync } from 'node:fs';
import { randomUUID } from 'node:crypto';
import {
    defaultPreferences, defaultProgress, decodeProgress, validateSettings, validatePreferences,
    filterTerms, makePool, resolveQuestionCount, makeQuestions, recordAttempt, toggleFavorite,
    mathDisplay, sanitizeSelection, normalizeAudioText, subjectsFromTerms, summarizeProgress,
    type Term, type QuizAttempt,
} from '../src/shared/core';

const terms = JSON.parse(readFileSync(new URL('../../Resources/terms.json', import.meta.url), 'utf8')) as Term[];
const attempt = (correct: boolean, termID = 104): QuizAttempt => ({
    id: randomUUID(), termID, direction: 'zhToEn', selectedOptionID: correct ? termID : 1,
    correctOptionID: termID, answeredAt: '2026-10-28T01:02:03Z',
});

test('canonical corpus has 326 stable IDs and 130 important entries', () => {
    assert.equal(terms.length, 326);
    assert.equal(terms.filter(t => t.important).length, 130);
    assert.deepEqual(terms.map(t => t.id), Array.from({ length: 326 }, (_, i) => i + 1));
});
test('all bundled terms have both IPA and independent speech text', () => {
    assert.ok(terms.every(t => t.speechText && t.pronunciation.americanIPA && t.pronunciation.britishIPA));
    assert.ok(terms.some(t => t.speechText !== t.english));
});
test('subject order and counts match Mac', () => {
    assert.deepEqual(subjectsFromTerms(terms).map(s => [s.id, s.count]), [
        ['subjects', 11], ['analysis', 163], ['algebra', 110], ['probability', 27], ['statistics', 15],
    ]);
});
test('default preferences match Mac', () => {
    assert.deepEqual(defaultPreferences(), { direction: 'mixed', scope: 'important', questionLength: 'twenty', selectedSubjectID: 'analysis', includeMastered: true });
});
test('missing progress fields get old-format defaults', () => assert.deepEqual(decodeProgress({}), defaultProgress()));
test('legacy selected subjects beat important-only flag and use first sorted ID', () => {
    const prefs = validatePreferences({ selectedSubjectIDs: ['statistics', 'algebra'], importantOnly: true, questionCount: 326 });
    assert.equal(prefs.scope, 'subject'); assert.equal(prefs.selectedSubjectID, 'algebra'); assert.equal(prefs.questionLength, 'all');
});
test('legacy count and important flag migration', () => {
    assert.equal(validatePreferences({ questionCount: 10 }).questionLength, 'ten');
    assert.equal(validatePreferences({ questionCount: 50 }).questionLength, 'fifty');
    assert.equal(validatePreferences({ questionCount: 15 }).questionLength, 'twenty');
    assert.equal(validatePreferences({ importantOnly: false }).scope, 'all');
});
test('new preferences round trip without losing scope', () => {
    const progress = { ...defaultProgress(), quizPreferences: { ...defaultPreferences(), scope: 'favorites' as const, questionLength: 'all' as const, includeMastered: false } };
    assert.deepEqual(decodeProgress(JSON.parse(JSON.stringify(progress))), progress);
});
test('malformed supplied data is rejected without silently dropping attempts', () => {
    for (const invalid of [null, [], { attempts: [{}] }, { favoriteIDs: ['1'] }, { correctStreak: { '1': -1 } }, { quizPreferences: { scope: 'bad' } }]) {
        assert.throws(() => decodeProgress(invalid));
    }
});
test('speech settings preserve legacy keys and validate supported speeds', () => {
    assert.deepEqual(validateSettings({ voiceLocale: 'en-GB', speechSpeed: 1.25 }), { accent: 'en-GB', speed: 1.25 });
    assert.throws(() => validateSettings({ accent: 'fr-FR', speed: 1 }));
    assert.throws(() => validateSettings({ speed: 2 }));
});
test('search is trim/case insensitive and combines all filters using AND', () => {
    const progress = { ...defaultProgress(), favoriteIDs: [1], wrongIDs: [1] };
    assert.deepEqual(filterTerms(terms, { query: '  MATHEMATICAL ANALYSIS ', subjectID: 'subjects', importantOnly: true, favoritesOnly: true, mistakesOnly: true }, progress).map(t => t.id), [1]);
    assert.equal(filterTerms(terms, { query: '', subjectID: 'algebra', importantOnly: true, favoritesOnly: true, mistakesOnly: false }, progress).length, 0);
});
test('search includes definitions but not IPA', () => {
    const filters = { query: '研究极限', importantOnly: false, favoritesOnly: false, mistakesOnly: false };
    assert.ok(filterTerms(terms, filters, defaultProgress()).some(t => t.id === 1));
    assert.equal(filterTerms(terms, { ...filters, query: 'ˌmæθəˈmætɪkəl' }, defaultProgress()).length, 0);
});
test('mastered exclusions apply to every quiz scope and real count', () => {
    const progress = { ...defaultProgress(), masteredIDs: [1], favoriteIDs: [1, 2], wrongIDs: [1] };
    for (const scope of ['all', 'important', 'subject', 'mistakes', 'favorites'] as const) {
        const pool = makePool(terms, { ...defaultPreferences(), scope, selectedSubjectID: 'subjects', includeMastered: false }, progress);
        assert.ok(!pool.some(t => t.id === 1));
    }
    assert.equal(makePool(terms, { ...defaultPreferences(), scope: 'favorites', includeMastered: false }, progress).length, 1);
});
test('10/20/50/all resolve against availability', () => {
    assert.equal(resolveQuestionCount('all', 326), 326);
    assert.equal(resolveQuestionCount('fifty', 13), 13);
    assert.equal(resolveQuestionCount('twenty', 130), 20);
    assert.equal(resolveQuestionCount('ten', 0), 0);
});
test('seeded questions are reproducible', () => {
    assert.deepEqual(makeQuestions(terms, terms, 50, 'mixed', 42), makeQuestions(terms, terms, 50, 'mixed', 42));
});
test('every quiz question has four distinct IDs including correct answer', () => {
    for (const question of makeQuestions(terms, terms, 326, 'mixed', 1)) {
        assert.equal(new Set(question.options.map(t => t.id)).size, 4);
        assert.ok(question.options.some(t => t.id === question.correctOptionID));
        assert.ok(question.direction === 'zhToEn' || question.direction === 'enToZh');
    }
});
test('one-item mistake/favorite pools still get full-corpus distractors', () => {
    assert.equal(makeQuestions([terms[103]], terms, 50, 'zhToEn', 1).length, 1);
    assert.equal(makeQuestions(terms.slice(0, 3), terms.slice(0, 3), 3, 'enToZh').length, 0);
});
test('quiz question IDs are unique and requested length never exceeds pool', () => {
    const questions = makeQuestions([...terms, terms[0]], terms, 999, 'zhToEn');
    assert.equal(questions.length, 326); assert.equal(new Set(questions.map(q => q.term.id)).size, 326);
});
test('distractors prefer the same subject when it contains sufficient terms', () => {
    const questions = makeQuestions([terms[0]], terms, 1, 'enToZh', 4);
    assert.ok(questions[0].options.every(t => t.subjectID === 'subjects'));
});
test('wrong -> first correct -> second correct -> mastered on third correct', () => {
    let progress = recordAttempt(defaultProgress(), attempt(false));
    assert.deepEqual(progress.wrongIDs, [104]);
    progress = recordAttempt(progress, attempt(true)); assert.deepEqual(progress.wrongIDs, [104]);
    progress = recordAttempt(progress, attempt(true)); assert.deepEqual(progress.wrongIDs, []);
    progress = recordAttempt(progress, attempt(true)); assert.deepEqual(progress.masteredIDs, [104]);
    progress = recordAttempt(progress, attempt(false)); assert.deepEqual(progress.masteredIDs, []); assert.equal(progress.correctStreak['104'], 0);
});
test('favorite status stays separate from learning state', () => {
    const before = defaultProgress(); let progress = toggleFavorite(before, 104);
    progress = recordAttempt(progress, attempt(false));
    assert.deepEqual(progress.favoriteIDs, [104]); assert.deepEqual(before.favoriteIDs, []);
    assert.deepEqual(toggleFavorite(progress, 104).favoriteIDs, []);
});
test('another word does not break per-term streak', () => {
    let progress = recordAttempt(defaultProgress(), attempt(true));
    progress = recordAttempt(progress, attempt(false, 2));
    progress = recordAttempt(progress, attempt(true)); assert.equal(progress.correctStreak['104'], 2);
});
test('progress summary distinguishes unique words from attempts', () => {
    let progress = recordAttempt(defaultProgress(), attempt(true));
    progress = recordAttempt(progress, attempt(true)); progress = recordAttempt(progress, attempt(false, 2));
    const summary = summarizeProgress(terms, progress);
    assert.equal(summary.attemptedCount, 2); assert.equal(summary.totalAttemptCount, 3); assert.equal(summary.accuracyPercentage, 67); assert.equal(summary.wrongCount, 1);
});
test('UTF-16 DOM selection can isolate unbounded, preserving Unicode', () => {
    const text = '📘 unbounded function';
    assert.equal(sanitizeSelection(text.slice(3, 12)), 'unbounded');
    assert.equal(sanitizeSelection('  “α-continuity”  '), 'α-continuity');
});
test('selection preserves multiple words, internal hyphens and apostrophes', () => {
    assert.equal(sanitizeSelection('“one-to-one function,”'), 'one-to-one function');
    assert.equal(sanitizeSelection("  L'Hôpital's rule. "), "L'Hôpital's rule");
});
test('whitespace and punctuation-only selections cannot be spoken', () => {
    for (const text of ['', '  ', '…，。', '—', '()']) assert.equal(sanitizeSelection(text), null);
});
test('audio lookup canonicalizes typography but preserves acronym case', () => {
    assert.equal(normalizeAudioText('L’Hôpital’s\u00a0rule'), "L'Hôpital's rule");
    assert.equal(normalizeAudioText('one–to–one  function'), 'one-to-one function');
    assert.equal(normalizeAudioText('CDF'), 'CDF');
});
test('math notation matches fixed substitution order', () => {
    assert.equal(mathDisplay('`A^\\mathsf{T}` \\in V'), 'Aᵀ ∈ V');
    assert.equal(mathDisplay('\\( \\xi \\le \\infty \\)'), ' ξ ≤ ∞ ');
});
