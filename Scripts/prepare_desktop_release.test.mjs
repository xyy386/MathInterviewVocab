import assert from 'node:assert/strict';
import test from 'node:test';
import { macAppcast } from './prepare_desktop_release.mjs';

const valid = { version: '1.4.0', build: '6', bytes: 50000000, signature: 'A'.repeat(86) + '==' };

test('appcast binds the official immutable archive, version and signature', () => {
    const xml = macAppcast(valid);
    assert.ok(xml.includes('/releases/download/v1.4.0/MathInterviewVocab-macOS-universal-v1.4.0.zip'));
    assert.ok(xml.includes('<sparkle:version>6</sparkle:version>'));
    assert.ok(xml.includes('length="50000000"'));
    assert.ok(xml.includes(`sparkle:edSignature="${valid.signature}"`));
    assert.ok(!xml.includes('releaseNotesLink'));
});

test('appcast rejects XML injection, prerelease and malformed signature or size', () => {
    for (const invalid of [{ version: '1.4.0-beta' }, { version: '<x>' }, { build: '0' },
        { build: '6</sparkle:version>' }, { bytes: -1 }, { bytes: Number.MAX_SAFE_INTEGER + 1 },
        { signature: 'not-a-signature' }]) {
        assert.throws(() => macAppcast({ ...valid, ...invalid }));
    }
});
