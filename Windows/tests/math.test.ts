import assert from 'node:assert/strict';
import test from 'node:test';
import { createHash } from 'node:crypto';
import { readFileSync } from 'node:fs';
import path from 'node:path';
import { mathSegments, mathSearchText } from '../src/shared/math-source.mjs';
import { defaultProgress, filterTerms, type Term } from '../src/shared/core';
import { createElement } from 'react';
import { renderToStaticMarkup } from 'react-dom/server';
import { RichMathText } from '../src/renderer/RichMathText';

const directory = path.resolve(import.meta.dirname, '../../Resources/Math');
const terms: Term[] = JSON.parse(readFileSync(path.resolve(directory, '../terms.json'), 'utf8'));
const manifest = JSON.parse(readFileSync(path.join(directory, 'manifest.json'), 'utf8'));
const hash = (data: Buffer) => createHash('sha256').update(data).digest('hex');
test('all 63 offline formula assets bind canonical vocabulary, dimensions and original LaTeX', () => {
    assert.equal(manifest.entries.length, 63);
    assert.equal(manifest.engine, 'MathJax 4.1.3');
    assert.ok(manifest.entries.find((entry: { tex: string }) => entry.tex.startsWith('F(x)=\\int')).widthEm > 8);
    assert.equal(manifest.termsSHA256, hash(readFileSync(path.resolve(directory, '../terms.json'))));
    let fields = 0; const ids = new Set<number>();
    for (const term of terms) for (const field of ['chinese', 'english', 'definition'] as const) {
        const math = mathSegments(term[field]).filter(part => part.kind === 'math');
        if (math.length) { fields += 1; ids.add(term.id); }
        for (const part of math) assert.ok(manifest.entries.some((entry: { tex: string; display: boolean }) => entry.tex === part.text && entry.display === part.display));
    }
    assert.equal(fields, 45); assert.equal(ids.size, 44);
    for (const asset of manifest.entries) {
        assert.ok(asset.widthEm > 0 && asset.heightEm > asset.depthEm && asset.depthEm >= 0);
        assert.equal(asset.svg, asset.id + '.svg'); assert.equal(asset.pdf, asset.id + '.pdf');
        for (const type of ['svg', 'pdf']) assert.equal(hash(readFileSync(path.join(directory, asset[type]))), asset[type + 'SHA256']);
        const svg = readFileSync(path.join(directory, asset.svg), 'utf8');
        assert.ok(svg.includes('<path')); assert.ok(!/<script|<image|<text|foreignObject|\son\w+=/.test(svg));
    }
});
test('segmentation preserves source, supports mixed Unicode and never guesses unmarked prose', () => {
    assert.deepEqual(mathSegments('中文🙂 \\(H_0\\) 和 \\[x_{ij}^2\\]'), [
        { kind: 'text', text: '中文🙂 ' }, { kind: 'math', text: 'H_0', source: '\\(H_0\\)', display: false },
        { kind: 'text', text: ' 和 ' }, { kind: 'math', text: 'x_{ij}^2', source: '\\[x_{ij}^2\\]', display: true },
    ]);
    assert.deepEqual(mathSegments('file_name and H_0'), [{ kind: 'text', text: 'file_name and H_0' }]);
});
for (const query of ['H_0', 'H₀', 'H0']) test(`search recognizes the visible subscript alias ${query}`, () => {
    assert.ok(filterTerms(terms, { query, importantOnly: false, favoritesOnly: false, mistakesOnly: false }, defaultProgress()).some(term => term.id === 323));
    assert.equal(mathSearchText('H_0'), mathSearchText(query));
});

test('nested fractions, roots, multi-character scripts and matrix fixtures have complete vector assets', () => {
    const fixtures = path.resolve(directory, '../../Tests/Fixtures/Math');
    const entries = JSON.parse(readFileSync(path.join(fixtures, 'manifest.json'), 'utf8')).entries;
    assert.equal(entries.length, 6);
    for (const asset of entries) for (const type of ['svg', 'pdf']) {
        assert.equal(hash(readFileSync(path.join(fixtures, asset[type]))), asset[type + 'SHA256']);
        assert.ok(asset.widthEm > 0 && asset.heightEm > asset.depthEm);
    }
});

test('missing formula keeps a visible error and the original copy source', () => {
    const source = '\\(unbundled_{formula}\\)';
    const output = renderToStaticMarkup(createElement(RichMathText, { source }));
    assert.ok(output.includes('公式显示异常'));
    assert.ok(output.includes('data-math-source="' + source + '"'));
});
