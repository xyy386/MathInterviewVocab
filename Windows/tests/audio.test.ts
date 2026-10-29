import test from 'node:test';
import assert from 'node:assert/strict';
import { readFileSync } from 'node:fs';
import { createHash } from 'node:crypto';
import { fileURLToPath } from 'node:url';
import { BundledAudio, type AudioManifest } from '../src/main/audio';
import { normalizeAudioText, sanitizeSelection, mathDisplay, type Term } from '../src/shared/core';

const resources = fileURLToPath(new URL('../resources/', import.meta.url));
const manifest = JSON.parse(readFileSync(new URL('../resources/audio-manifest.json', import.meta.url), 'utf8')) as AudioManifest & { coverage: { complete: boolean; expectedEntries: number } };
const terms = JSON.parse(readFileSync(new URL('../../Resources/terms.json', import.meta.url), 'utf8')) as Term[];
const audioKey = (text: string, accent: string) => createHash('sha256').update(`${accent}\n${normalizeAudioText(sanitizeSelection(text) ?? '')}`).digest('hex');

test('release audio manifest is complete and truthfully names its open-source voice', () => {
    assert.equal(manifest.coverage.complete, true);
    assert.equal(Object.keys(manifest.entries).length, manifest.coverage.expectedEntries);
    assert.match(manifest.source, /Kokoro|Piper|开源/);
    assert.doesNotMatch(manifest.source, /Google/i);
});
test('every complete speechText has both bundled accents without a network fallback', () => {
    for (const term of terms) for (const accent of ['en-US', 'en-GB']) {
        assert.ok(manifest.entries[audioKey(term.speechText, accent)], `Missing ${term.id} ${accent}: ${term.speechText}`);
    }
});
test('every complete Latin word displayed in the glossary has both accents', () => {
    for (const term of terms) {
        const words = normalizeAudioText(mathDisplay(term.english)).match(/[\p{Script=Latin}\p{N}]+(?:['-][\p{Script=Latin}\p{N}]+)*/gu) ?? [];
        for (const word of words) for (const accent of ['en-US', 'en-GB']) {
            assert.ok(manifest.entries[audioKey(word, accent)], `Missing ${accent}: ${word}`);
        }
    }
});
test('every clip has a canonical name, verified bytes, hash and MPEG header', () => {
    for (const [key, entry] of Object.entries(manifest.entries)) {
        assert.equal(key, audioKey(entry.text, entry.accent));
        assert.equal(entry.file, `${key}.mp3`);
        const buffer = readFileSync(new URL(`../resources/audio/${entry.file}`, import.meta.url));
        assert.ok(buffer.length >= 128); assert.equal(buffer.length, entry.bytes);
        assert.equal(createHash('sha256').update(buffer).digest('hex'), entry.sha256);
        assert.ok(buffer.subarray(0, 3).toString() === 'ID3' || (buffer[0] === 0xff && (buffer[1] & 0xe0) === 0xe0));
    }
});
test('American and British inventories cover exactly the same texts', () => {
    const texts = (accent: string) => Object.values(manifest.entries).filter(e => e.accent === accent).map(e => e.text).sort();
    assert.deepEqual(texts('en-US'), texts('en-GB'));
});
test('unbounded and full phrase resolve to local resource URLs', () => {
    const audio = new BundledAudio(resources);
    for (const accent of ['en-US', 'en-GB']) for (const text of ['unbounded', 'unbounded function', '“unbounded,”']) {
        const result = audio.find(text, accent); assert.equal(result.status, 'ready'); assert.match(result.url!, /^vocab:\/\/bundle\/audio\/[a-f0-9]{64}\.mp3$/);
    }
});
test('US and UK samples are not a relabeled copy of the same audio', () => {
    for (const text of ['unbounded function', 'Mathematical Analysis', 'Probability Theory']) {
        const us = manifest.entries[audioKey(text, 'en-US')], uk = manifest.entries[audioKey(text, 'en-GB')];
        assert.ok(us && uk); assert.notEqual(us.sha256, uk.sha256);
    }
});
test('unknown/partial text gives explicit missing; invalid requests cannot access files', () => {
    const audio = new BundledAudio(resources);
    assert.equal(audio.find('zzzz-unrecorded-selection', 'en-US').status, 'missing');
    assert.equal(audio.find('“ … ”', 'en-GB').status, 'missing');
    assert.throws(() => audio.find('../progress.json', 'fr-FR'));
    assert.throws(() => audio.find('x'.repeat(601), 'en-US'));
});
