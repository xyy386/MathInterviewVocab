import test from 'node:test';
import assert from 'node:assert/strict';
import { mkdtempSync, writeFileSync, readFileSync, readdirSync, rmSync } from 'node:fs';
import { tmpdir } from 'node:os';
import path from 'node:path';
import { randomUUID } from 'node:crypto';
import { LocalRepository } from '../src/main/persistence';

function withRepository(run: (directory: string) => void): void {
    const directory = mkdtempSync(path.join(tmpdir(), 'math-vocab-storage-'));
    try { run(directory); } finally { rmSync(directory, { recursive: true }); }
}
const ids = new Set(Array.from({ length: 326 }, (_, i) => i + 1));
test('favorites, settings and completed answers survive process reload', () => withRepository(directory => {
    const repository = new LocalRepository(directory, ids);
    repository.favorite(104);
    repository.setSettings({ accent: 'en-GB', speed: 0.75 });
    repository.answer({ id: randomUUID(), termID: 104, correctOptionID: 104, selectedOptionID: 1, direction: 'enToZh', answeredAt: '2026-10-28T02:00:00Z' });
    const reloaded = new LocalRepository(directory, ids);
    assert.deepEqual(reloaded.progress.favoriteIDs, [104]); assert.deepEqual(reloaded.progress.wrongIDs, [104]);
    assert.equal(reloaded.progress.attempts.length, 1); assert.deepEqual(reloaded.settings, { accent: 'en-GB', speed: 0.75 });
}));
test('one attempt UUID cannot be saved twice', () => withRepository(directory => {
    const repository = new LocalRepository(directory, ids);
    const attempt = { id: randomUUID(), termID: 104, correctOptionID: 104, selectedOptionID: 104, direction: 'zhToEn', answeredAt: '2026-10-28T02:00:00Z' };
    repository.answer(attempt); repository.answer(attempt); assert.equal(repository.progress.attempts.length, 1);
}));
test('corrupt progress is not silently overwritten and reset preserves backup', () => withRepository(directory => {
    const original = '{broken JSON';
    writeFileSync(path.join(directory, 'progress.json'), original);
    const repository = new LocalRepository(directory, ids);
    assert.ok(repository.error); assert.throws(() => repository.favorite(104));
    assert.equal(readFileSync(path.join(directory, 'progress.json'), 'utf8'), original);
    repository.reset();
    const backup = readdirSync(directory).find(name => name.startsWith('progress.json.backup-'))!;
    assert.equal(readFileSync(path.join(directory, backup), 'utf8'), original);
    assert.equal(new LocalRepository(directory, ids).progress.attempts.length, 0);
}));
test('reset clears records but preserves quiz and voice preferences', () => withRepository(directory => {
    const repository = new LocalRepository(directory, ids);
    repository.preferences({ direction: 'enToZh', scope: 'favorites', questionLength: 'all', selectedSubjectID: 'statistics', includeMastered: false });
    repository.setSettings({ accent: 'en-GB', speed: 1.25 }); repository.favorite(104); repository.reset();
    assert.deepEqual(repository.progress.favoriteIDs, []); assert.equal(repository.progress.quizPreferences.scope, 'favorites');
    assert.equal(repository.settings.accent, 'en-GB'); assert.equal(repository.settings.speed, 1.25);
}));
test('unknown term IDs and malicious settings are rejected', () => withRepository(directory => {
    const repository = new LocalRepository(directory, ids);
    assert.throws(() => repository.favorite(327)); assert.throws(() => repository.setSettings({ accent: "'; rm -rf /", speed: 1 }));
}));
test('recovering voice settings cannot hide corrupt progress protection', () => withRepository(directory => {
    writeFileSync(path.join(directory, 'progress.json'), '{broken progress');
    writeFileSync(path.join(directory, 'settings.json'), '{broken settings');
    const repository = new LocalRepository(directory, ids);
    repository.setSettings({ accent: 'en-US', speed: 1 });
    assert.match(repository.error!, /学习记录无法读取/);
    assert.doesNotMatch(repository.error!, /发音设置无法读取/);
    assert.throws(() => repository.favorite(104));
    repository.reset(); assert.equal(repository.error, null);
}));
