import assert from 'node:assert/strict';
import test from 'node:test';
import { desktopIndex, macAppcast } from './prepare_desktop_release.mjs';

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

test('platform index has a fixed non-cyclic schema and immutable attachment identities', () => {
    const asset = name => ({ name, bytes: 100, sha256: 'a'.repeat(64) });
    const value = { platform: 'macos-universal', version: '1.5.0', build: '7', notes: 'n'.repeat(9000),
        assets: [asset('MathInterviewVocab-macOS-universal-v1.5.0.zip'), asset('appcast.xml')] };
    const index = JSON.parse(desktopIndex(value));
    assert.equal(index.notes.length, 8000);
    assert.deepEqual(Object.keys(index).sort(), ['assets', 'build', 'notes', 'platform', 'schema', 'version']);
    assert.throws(() => desktopIndex({ ...value, assets: [...value.assets, asset('SHA256SUMS.txt')] }));
    assert.throws(() => desktopIndex({ ...value, platform: 'android' }));
    assert.throws(() => desktopIndex({ ...value, build: '0' }));
    assert.throws(() => desktopIndex({ ...value, version: '1.5.0-beta' }));
});
