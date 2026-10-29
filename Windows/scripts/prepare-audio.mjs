import { createHash } from 'node:crypto';
import {
    copyFile,
    mkdir,
    readFile,
    rename,
    stat,
    writeFile,
} from 'node:fs/promises';
import path from 'node:path';
import process from 'node:process';
import { fileURLToPath } from 'node:url';

const scriptDirectory = path.dirname(fileURLToPath(import.meta.url));
const windowsDirectory = path.resolve(scriptDirectory, '..');
const projectDirectory = path.resolve(windowsDirectory, '..');
const termsPath = path.join(projectDirectory, 'Resources', 'terms.json');
const resourcesDirectory = path.join(windowsDirectory, 'resources');
const audioDirectory = path.join(resourcesDirectory, 'audio');
const manifestPath = path.join(resourcesDirectory, 'audio-manifest.json');

const ACCENTS = ['en-US', 'en-GB'];
const DEFAULT_SOURCE = 'No bundled audio imported';
const MINIMUM_MP3_BYTES = 128;

function sanitizeSelection(selection) {
    const value = selection.replace(/^[\p{P}\s]+|[\p{P}\s]+$/gu, '');
    return value && /[\p{L}\p{N}]/u.test(value) ? value : null;
}

function normalizeAudioText(text) {
    return text
        .normalize('NFKC')
        .replace(/[‘’ʼ＇]/g, "'")
        .replace(/[‐‑‒–—−]/g, '-')
        .replace(/\s+/gu, ' ')
        .trim();
}

function mathDisplay(source) {
    let text = source
        .replaceAll('`', '')
        .replaceAll('\\(', '')
        .replaceAll('\\)', '')
        .replaceAll('\\[', '')
        .replaceAll('\\]', '');

    const replacements = [
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

function audioKey(accent, text) {
    return createHash('sha256').update(`${accent}\n${text}`, 'utf8').digest('hex');
}

function contiguousWordSubstrings(source) {
    const normalized = normalizeAudioText(source);
    const pattern = /[\p{Script=Latin}\p{N}]+(?:['-][\p{Script=Latin}\p{N}]+)*/gu;
    const words = [...normalized.matchAll(pattern)].map(match => ({
        text: match[0],
        start: match.index,
        end: match.index + match[0].length,
    }));
    const values = new Set();

    for (let start = 0; start < words.length; start += 1) {
        for (let end = start; end < words.length; end += 1) {
            const wordRange = words.slice(start, end + 1);

            // 覆盖实际框选文本，保留分号、斜线、括号等内部标点。
            const sourceValue = normalized.slice(words[start].start, words[end].end);
            if (!/[\p{Script=Han}\p{Script=Greek}\p{S}]/u.test(sourceValue)) values.add(sourceValue);

            // 同时覆盖只选择完整单词后由空格连接的常用组合。
            values.add(wordRange.map(word => word.text).join(' '));
        }
    }
    return [...values];
}

function buildDesiredInventory(terms) {
    const speechTexts = new Set();
    const speechSubstrings = new Set();
    const displaySubstrings = new Set();
    const texts = new Set();

    for (const term of terms) {
        const sanitizedSpeech = sanitizeSelection(term.speechText);
        if (!sanitizedSpeech) throw new Error(`词条 ${term.id} 缺少可朗读 speechText。`);

        const speechText = normalizeAudioText(sanitizedSpeech);
        speechTexts.add(speechText);
        texts.add(speechText);

        for (const value of contiguousWordSubstrings(speechText)) {
            speechSubstrings.add(value);
            texts.add(value);
        }
        for (const value of contiguousWordSubstrings(mathDisplay(term.english))) {
            displaySubstrings.add(value);
            texts.add(value);
        }
    }

    const desired = new Map();
    for (const text of [...texts].sort()) {
        for (const accent of ACCENTS) {
            const key = audioKey(accent, text);
            desired.set(key, { key, text, accent, file: `${key}.mp3` });
        }
    }

    return {
        desired,
        counts: {
            terms: terms.length,
            uniqueSpeechTexts: speechTexts.size,
            uniqueSpeechSubstrings: speechSubstrings.size,
            uniqueDisplaySubstrings: displaySubstrings.size,
            uniqueTexts: texts.size,
            expectedEntries: desired.size,
        },
    };
}

function isRecord(value) {
    return Boolean(value) && typeof value === 'object' && !Array.isArray(value);
}

async function readJson(file) {
    return JSON.parse(await readFile(file, 'utf8'));
}

function validateManifestShape(manifest, label) {
    if (!isRecord(manifest) || manifest.version !== 1) throw new Error(`${label} version 必须为 1。`);
    if (typeof manifest.source !== 'string' || !manifest.source.trim()) throw new Error(`${label} source 必须为非空字符串。`);
    if (!isRecord(manifest.entries)) throw new Error(`${label} entries 必须为对象。`);
}

function hasMpegFrame(buffer) {
    let start = 0;
    if (buffer.length >= 10 && buffer.subarray(0, 3).toString('ascii') === 'ID3') {
        const size = ((buffer[6] & 0x7f) << 21)
            | ((buffer[7] & 0x7f) << 14)
            | ((buffer[8] & 0x7f) << 7)
            | (buffer[9] & 0x7f);
        start = 10 + size;
    }

    const searchEnd = Math.min(buffer.length - 1, start + 8192);
    for (let index = start; index < searchEnd; index += 1) {
        const first = buffer[index];
        const second = buffer[index + 1];
        if (first !== 0xff || (second & 0xe0) !== 0xe0) continue;

        const version = (second >> 3) & 0x03;
        const layer = (second >> 1) & 0x03;
        const bitrate = (buffer[index + 2] >> 4) & 0x0f;
        const sampleRate = (buffer[index + 2] >> 2) & 0x03;
        if (version !== 0x01 && layer !== 0x00 && bitrate !== 0x00 && bitrate !== 0x0f && sampleRate !== 0x03) {
            return true;
        }
    }
    return false;
}

async function inspectMp3(file) {
    const buffer = await readFile(file);
    if (buffer.length < MINIMUM_MP3_BYTES) throw new Error(`MP3 小于 ${MINIMUM_MP3_BYTES} 字节。`);
    if (!hasMpegFrame(buffer)) throw new Error('未检测到有效 MPEG 音频帧。');
    return {
        bytes: buffer.length,
        sha256: createHash('sha256').update(buffer).digest('hex'),
    };
}

function validateEntryIdentity(key, entry, desired, label) {
    if (!/^[a-f0-9]{64}$/.test(key)) throw new Error(`${label} key 不是 SHA-256。`);
    if (!isRecord(entry)) throw new Error(`${label} 条目必须为对象。`);
    if (!desired) throw new Error(`${label} 不属于当前词库库存。`);
    if (entry.text !== desired.text || entry.accent !== desired.accent || entry.file !== desired.file) {
        throw new Error(`${label} 的 text/accent/file 与库存不一致。`);
    }
    if (!/^[a-f0-9]{64}$/.test(entry.sha256)) throw new Error(`${label} 缺少有效音频 SHA-256。`);
    if (!Number.isSafeInteger(entry.bytes) || entry.bytes < MINIMUM_MP3_BYTES) throw new Error(`${label} bytes 无效。`);
}

async function validateEntryFile(key, entry, desired, directory, label) {
    validateEntryIdentity(key, entry, desired, label);
    const file = path.join(directory, entry.file);
    const actual = await inspectMp3(file);
    if (actual.bytes !== entry.bytes) throw new Error(`${label} 字节数不一致：${actual.bytes} != ${entry.bytes}。`);
    if (actual.sha256 !== entry.sha256) throw new Error(`${label} 音频 SHA-256 不一致。`);
    return { file, actual };
}

async function loadCurrentEntries(inventory) {
    try {
        const manifest = await readJson(manifestPath);
        validateManifestShape(manifest, '当前清单');
        const entries = {};
        const failures = [];
        for (const [key, entry] of Object.entries(manifest.entries)) {
            try {
                const desired = inventory.desired.get(key);
                await validateEntryFile(key, entry, desired, audioDirectory, `当前清单 ${key}`);
                entries[key] = entry;
            } catch (error) {
                failures.push({ key, error: error.message });
            }
        }
        return { source: manifest.source, entries, failures };
    } catch (error) {
        if (error.code === 'ENOENT') return { source: DEFAULT_SOURCE, entries: {}, failures: [] };
        throw error;
    }
}

function buildManifest(inventory, source, entries, failures = []) {
    const sortedEntries = Object.fromEntries(Object.entries(entries).sort(([left], [right]) => left.localeCompare(right)));
    const missing = [];
    for (const [key, desired] of inventory.desired) {
        if (!sortedEntries[key]) missing.push(desired);
    }

    return {
        version: 1,
        source,
        generatedAt: new Date().toISOString(),
        entries: sortedEntries,
        coverage: {
            ...inventory.counts,
            availableEntries: Object.keys(sortedEntries).length,
            missingEntries: missing.length,
            complete: missing.length === 0 && failures.length === 0,
            missing,
            failures,
        },
    };
}

async function writeManifest(manifest) {
    await mkdir(resourcesDirectory, { recursive: true });
    const temporary = `${manifestPath}.tmp-${process.pid}`;
    await writeFile(temporary, `${JSON.stringify(manifest, null, 2)}\n`, 'utf8');
    await rename(temporary, manifestPath);
}

async function inventoryMode(inventory) {
    const current = await loadCurrentEntries(inventory);
    const manifest = buildManifest(inventory, current.source, current.entries, current.failures);
    await writeManifest(manifest);
    printSummary('库存已刷新', manifest);
}

async function findSourceManifest(importDirectory) {
    const candidates = [
        path.join(importDirectory, 'audio-manifest.json'),
        path.join(path.dirname(importDirectory), 'audio-manifest.json'),
    ];
    for (const candidate of candidates) {
        try {
            await stat(candidate);
            return candidate;
        } catch (error) {
            if (error.code !== 'ENOENT') throw error;
        }
    }
    throw new Error('导入目录旁未找到 audio-manifest.json。');
}

async function findSourceAudio(importDirectory, file) {
    const candidates = [
        path.join(importDirectory, file),
        path.join(importDirectory, 'audio', file),
    ];
    for (const candidate of candidates) {
        try {
            const value = await stat(candidate);
            if (value.isFile()) return candidate;
        } catch (error) {
            if (error.code !== 'ENOENT') throw error;
        }
    }
    throw new Error('源 MP3 不存在。');
}

async function copyAtomically(source, destination) {
    const temporary = `${destination}.tmp-${process.pid}`;
    await copyFile(source, temporary);
    await rename(temporary, destination);
}

async function importMode(inventory, importDirectory) {
    const sourceManifestPath = await findSourceManifest(importDirectory);
    const sourceManifest = await readJson(sourceManifestPath);
    validateManifestShape(sourceManifest, '源清单');

    const current = await loadCurrentEntries(inventory);
    const entries = { ...current.entries };
    const failures = [...current.failures];
    const currentCount = Object.keys(entries).length;
    if (currentCount > 0 && current.source !== sourceManifest.source) {
        throw new Error(`已有音频来源为“${current.source}”，不能混入“${sourceManifest.source}”。`);
    }

    await mkdir(audioDirectory, { recursive: true });
    const candidates = Object.entries(sourceManifest.entries);
    let imported = 0;
    let processed = 0;
    for (const [key, entry] of candidates) {
        processed += 1;
        const desired = inventory.desired.get(key);
        try {
            validateEntryIdentity(key, entry, desired, `源清单 ${key}`);
            const sourceAudio = await findSourceAudio(importDirectory, entry.file);
            const actual = await inspectMp3(sourceAudio);
            if (actual.bytes !== entry.bytes) throw new Error(`字节数不一致：${actual.bytes} != ${entry.bytes}。`);
            if (actual.sha256 !== entry.sha256) throw new Error('音频 SHA-256 不一致。');

            const destination = path.join(audioDirectory, entry.file);
            await copyAtomically(sourceAudio, destination);
            const copied = await inspectMp3(destination);
            if (copied.bytes !== actual.bytes || copied.sha256 !== actual.sha256) throw new Error('导入后复核失败。');
            entries[key] = entry;
            imported += 1;
        } catch (error) {
            failures.push({ key, text: desired?.text, accent: desired?.accent, error: error.message });
        }
        if (processed % 30 === 0 || processed === candidates.length) {
            console.log(`导入进度 ${processed}/${candidates.length}，成功 ${imported}，失败 ${failures.length}`);
        }
    }

    if (isRecord(sourceManifest.coverage) && Array.isArray(sourceManifest.coverage.failures)) {
        for (const failure of sourceManifest.coverage.failures) failures.push(failure);
    }

    const source = Object.keys(entries).length ? sourceManifest.source : DEFAULT_SOURCE;
    const manifest = buildManifest(inventory, source, entries, failures);
    await writeManifest(manifest);
    printSummary('导入完成', manifest);
    if (failures.length > 0) process.exitCode = 2;
}

async function verifyMode(inventory, requireComplete) {
    const manifest = await readJson(manifestPath);
    validateManifestShape(manifest, '当前清单');
    const failures = [];
    let checked = 0;
    for (const [key, entry] of Object.entries(manifest.entries)) {
        try {
            await validateEntryFile(key, entry, inventory.desired.get(key), audioDirectory, `当前清单 ${key}`);
        } catch (error) {
            failures.push({ key, error: error.message });
        }
        checked += 1;
        if (checked % 30 === 0 || checked === Object.keys(manifest.entries).length) {
            console.log(`校验进度 ${checked}/${Object.keys(manifest.entries).length}`);
        }
    }

    const missingEntries = inventory.desired.size - Object.keys(manifest.entries).length;
    console.log(`校验完成：可用 ${Object.keys(manifest.entries).length}/${inventory.desired.size}，缺失 ${missingEntries}，错误 ${failures.length}。`);
    if (failures.length > 0 || (requireComplete && missingEntries > 0)) process.exitCode = 2;
}

function printSummary(label, manifest) {
    console.log(`${label}：${manifest.coverage.availableEntries}/${manifest.coverage.expectedEntries} 条音频可用。`);
    console.log(`唯一文本 ${manifest.coverage.uniqueTexts}，缺失 ${manifest.coverage.missingEntries}，失败 ${manifest.coverage.failures.length}。`);
    console.log(`清单：${manifestPath}`);
}

function parseArguments(arguments_) {
    const result = { mode: 'inventory', importDirectory: null, requireComplete: false };
    for (let index = 0; index < arguments_.length; index += 1) {
        const argument = arguments_[index];
        if (argument === '--inventory') result.mode = 'inventory';
        else if (argument === '--verify') result.mode = 'verify';
        else if (argument === '--require-complete') result.requireComplete = true;
        else if (argument === '--import') {
            result.mode = 'import';
            result.importDirectory = arguments_[index + 1];
            index += 1;
            if (!result.importDirectory) throw new Error('--import 需要目录参数。');
        } else if (argument === '--help' || argument === '-h') result.mode = 'help';
        else throw new Error(`未知参数：${argument}`);
    }
    return result;
}

function printHelp() {
    console.log(`用法：
  node scripts/prepare-audio.mjs --inventory
  node scripts/prepare-audio.mjs --import <带 audio-manifest.json 的目录>
  node scripts/prepare-audio.mjs --verify [--require-complete]

默认只生成或刷新目标库存，不联网下载音频。导入时逐条验证文件名、MPEG 帧、字节数和 SHA-256；
失败与缺失会保留在 resources/audio-manifest.json，不会标记为可用。`);
}

async function main() {
    const options = parseArguments(process.argv.slice(2));
    if (options.mode === 'help') {
        printHelp();
        return;
    }

    const terms = await readJson(termsPath);
    if (!Array.isArray(terms) || terms.length !== 326) throw new Error('canonical terms.json 必须包含 326 条词汇。');
    const inventory = buildDesiredInventory(terms);

    const testValue = normalizeAudioText(sanitizeSelection('“  Green’s semi–bounded\tUMV! ”') ?? '');
    if (testValue !== "Green's semi-bounded UMV") throw new Error('音频文本规范化协议自检失败。');
    if (inventory.counts.expectedEntries !== inventory.counts.uniqueTexts * ACCENTS.length) {
        throw new Error(`库存自检失败：${inventory.counts.uniqueTexts} 个文本，${inventory.counts.expectedEntries} 条音频。`);
    }

    if (options.mode === 'inventory') await inventoryMode(inventory);
    else if (options.mode === 'import') await importMode(inventory, path.resolve(options.importDirectory));
    else if (options.mode === 'verify') await verifyMode(inventory, options.requireComplete);
}

main().catch(error => {
    console.error(`音频准备失败：${error.message}`);
    process.exitCode = 1;
});
