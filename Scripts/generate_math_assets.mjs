import assert from 'node:assert/strict';
import { createHash } from 'node:crypto';
import { execFileSync } from 'node:child_process';
import { mkdir, readFile, writeFile, copyFile } from 'node:fs/promises';
import { createRequire } from 'node:module';
import path from 'node:path';
import { fileURLToPath } from 'node:url';
import { mathSegments } from '../Windows/src/shared/math-source.mjs';

const root = path.resolve(path.dirname(fileURLToPath(import.meta.url)), '..');
const require = createRequire(path.join(root, 'Windows/package.json'));
const hash = data => createHash('sha256').update(data).digest('hex');
const directory = path.join(root, 'Resources/Math');
const input = await readFile(path.join(root, 'Resources/terms.json'));
const terms = JSON.parse(input);
const all = new Map();
let fields = 0; const termIDs = new Set();
for (const term of terms) for (const field of ['chinese', 'english', 'definition']) {
    const segments = mathSegments(term[field]);
    if (!segments.some(segment => segment.kind === 'math')) continue;
    fields += 1; termIDs.add(term.id);
    for (const segment of segments.filter(segment => segment.kind === 'math')) {
        const id = hash(`${segment.display ? 'display' : 'inline'}\n${segment.text}`);
        if (!all.has(id)) all.set(id, { id, tex: segment.text, display: segment.display, locations: [] });
        all.get(id).locations.push({ termID: term.id, field });
    }
}
assert.equal(all.size, 63); assert.equal(fields, 45); assert.equal(termIDs.size, 44);

if (process.argv.includes('--verify')) {
    const manifest = JSON.parse(await readFile(path.join(directory, 'manifest.json'), 'utf8'));
    assert.equal(manifest.engine, 'MathJax 4.1.3'); assert.equal(manifest.termsSHA256, hash(input));
    assert.equal(manifest.entries.length, all.size);
    for (const entry of manifest.entries) {
        assert.equal(all.get(entry.id)?.tex, entry.tex);
        for (const type of ['svg', 'pdf']) {
            assert.equal(entry[type], `${entry.id}.${type}`);
            assert.equal(hash(await readFile(path.join(directory, entry[type]))), entry[`${type}SHA256`]);
        }
        assert.equal(hash(entry.svgMarkup + '\n'), entry.svgSHA256);
        assert.ok(entry.widthEm > 0 && entry.heightEm > 0 && entry.depthEm >= 0);
    }
    console.log('Verified 63 formulas / 44 terms / 45 fields, SVG and vector PDF hashes.');
    process.exit(0);
}

// 固定引擎与本地字体，禁止运行时下载扩展或字体。
assert.equal(require('@mathjax/src/package.json').version, '4.1.3');
const { mathjax } = require('@mathjax/src/js/mathjax.js');
const { TeX } = require('@mathjax/src/js/input/tex.js');
const { SVG } = require('@mathjax/src/js/output/svg.js');
const { liteAdaptor } = require('@mathjax/src/js/adaptors/liteAdaptor.js');
const { RegisterHTMLHandler } = require('@mathjax/src/js/handlers/html.js');
const { MathJaxNewcmFont } = require('@mathjax/mathjax-newcm-font/js/svg.js');
require('@mathjax/src/js/util/asyncLoad/node.js');
require('@mathjax/src/js/input/tex/base/BaseConfiguration.js');
require('@mathjax/src/js/input/tex/ams/AmsConfiguration.js');
const adaptor = liteAdaptor(); RegisterHTMLHandler(adaptor);
const document = mathjax.document('', { InputJax: new TeX({ packages: ['base', 'ams'], formatError(_jax, error) { throw error; } }),
    OutputJax: new SVG({ fontCache: 'none', fontData: MathJaxNewcmFont, linebreaks: { inline: false }, displayOverflow: 'overflow' }) });

async function generate(entries, output) {
    await mkdir(output, { recursive: true });
    const result = [];
    for (const entry of entries) {
        try {
            const node = await document.convertPromise(entry.tex, { display: entry.display });
            const svgNodes = adaptor.tags(node, 'svg');
            assert.equal(svgNodes.length, 1, 'Each atomic formula must have exactly one complete SVG');
            const svgNode = svgNodes[0];
            const box = adaptor.getAttribute(svgNode, 'viewBox').split(/\s+/).map(Number);
            const padding = 30;
            const [x, y, width, height] = [box[0] - padding, box[1] - padding, box[2] + padding * 2, box[3] + padding * 2];
            adaptor.setAttribute(svgNode, 'viewBox', `${x} ${y} ${width} ${height}`);
            adaptor.setAttribute(svgNode, 'width', `${width / 1000}em`);
            adaptor.setAttribute(svgNode, 'height', `${height / 1000}em`);
            adaptor.setAttribute(svgNode, 'style', '');
            const svg = adaptor.outerHTML(svgNode);
            assert.ok(!/<(?:script|image|foreignObject|text)\b|\son\w+=|https?:\/\/(?!www.w3.org)/i.test(svg), 'SVG must be self-contained paths');
            await writeFile(path.join(output, `${entry.id}.svg`), svg + '\n');
            result.push({ ...entry, widthEm: width / 1000, heightEm: height / 1000, depthEm: Math.max(0, (y + height) / 1000),
                svg: `${entry.id}.svg`, pdf: `${entry.id}.pdf`, svgMarkup: svg, svgSHA256: hash(svg + '\n') });
        } catch (error) { throw new Error(`Formula failed: ${JSON.stringify(entry.locations)} ${entry.tex}: ${error.message}`); }
    }
    await writeFile(path.join(output, 'manifest.json'), JSON.stringify({ schema: 1, engine: 'MathJax 4.1.3', termsSHA256: hash(input), entries: result }, null, 2) + '\n');
    execFileSync(require('electron'), [path.join(root, 'Scripts/math_assets_pdf.mjs'), output], { stdio: 'inherit', env: process.env });
}
await generate([...all.values()], directory);
const fixtures = JSON.parse(await readFile(path.join(root, 'Tests/Fixtures/math-formulas.json'), 'utf8'));
await generate(fixtures.map(tex => ({ id: hash(`display\n${tex}`), tex, display: true, locations: [{ field: 'test fixture' }] })), path.join(root, 'Tests/Fixtures/Math'));
await copyFile(require.resolve('@mathjax/src/LICENSE'), path.join(directory, 'MathJax-LICENSE.txt'));
console.log('Generated offline SVG/PDF math assets. No vocabulary or audio files were changed.');
