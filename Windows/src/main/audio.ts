import { createHash } from 'node:crypto';
import { readFileSync, existsSync } from 'node:fs';
import path from 'node:path';
import { normalizeAudioText, sanitizeSelection, type Accent } from '../shared/core';

interface AudioEntry { text: string; accent: Accent; file: string; sha256: string; bytes: number }
export interface AudioManifest {
    version: number;
    source: string;
    entries: Record<string, AudioEntry>;
    coverage?: Record<string, unknown>;
}

export class BundledAudio {
    private readonly manifest: AudioManifest;

    constructor(private readonly directory: string) {
        this.manifest = JSON.parse(readFileSync(path.join(directory, 'audio-manifest.json'), 'utf8')) as AudioManifest;
        if (!this.manifest.entries || typeof this.manifest.entries !== 'object') throw new Error('内置发音索引损坏。');
    }

    get info() {
        const entries = Object.values(this.manifest.entries);
        return { clips: entries.length, texts: new Set(entries.map(entry => entry.text)).size, source: this.manifest.source };
    }

    find(input: unknown, accent: unknown): { url: string | null; status: 'ready' | 'missing'; matchedText?: string } {
        if (typeof input !== 'string' || input.length > 600 || !['en-US', 'en-GB'].includes(String(accent))) {
            throw new Error('无效的朗读请求。');
        }
        const text = normalizeAudioText(sanitizeSelection(input) ?? '');
        const key = createHash('sha256').update(`${accent}\n${text}`).digest('hex');
        const entry = this.manifest.entries[key];
        if (!entry || !/^[a-f0-9]{64}\.mp3$/.test(entry.file) || !existsSync(path.join(this.directory, 'audio', entry.file))) {
            return { url: null, status: 'missing' };
        }
        return { url: `vocab://bundle/audio/${entry.file}`, status: 'ready', matchedText: entry.text };
    }
}
