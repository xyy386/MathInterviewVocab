import { mkdirSync, readFileSync, existsSync, writeFileSync, renameSync, copyFileSync } from 'node:fs';
import path from 'node:path';
import { randomUUID } from 'node:crypto';
import {
    decodeProgress, defaultProgress, validatePreferences, validateSettings,
    recordAttempt, toggleFavorite,
    type ProgressData, type QuizAttempt, type QuizPreferences, type SpeechSettings,
} from '../shared/core';

function atomicWrite(file: string, value: unknown): void {
    const temporary = `${file}.${randomUUID()}.tmp`;
    writeFileSync(temporary, JSON.stringify(value, null, 2) + '\n', { encoding: 'utf8', mode: 0o600 });
    renameSync(temporary, file);
}

/** 在用户目录保存，绝不写回解压目录或 Mac 现有进度。 */
export class LocalRepository {
    progress: ProgressData = defaultProgress();
    settings: SpeechSettings = { accent: 'en-US', speed: 1 };
    private progressError: string | null = null;
    private settingsError: string | null = null;
    private progressBlocked = false;
    private settingsBlocked = false;
    private readonly progressFile: string;
    private readonly settingsFile: string;

    constructor(readonly directory: string, private readonly termIDs: ReadonlySet<number>) {
        mkdirSync(directory, { recursive: true });
        this.progressFile = path.join(directory, 'progress.json');
        this.settingsFile = path.join(directory, 'settings.json');
        try {
            if (existsSync(this.progressFile)) {
                this.progress = decodeProgress(JSON.parse(readFileSync(this.progressFile, 'utf8')));
                this.validateTermIDs(this.progress);
            }
        } catch (error) {
            this.progressBlocked = true;
            this.progressError = `学习记录无法读取，原文件已保留。可在设置中确认重置（将先备份原文件）。${String(error)}`;
        }
        try {
            if (existsSync(this.settingsFile)) {
                this.settings = validateSettings(JSON.parse(readFileSync(this.settingsFile, 'utf8')));
            }
        } catch (error) {
            this.settingsBlocked = true;
            this.settingsError = `发音设置无法读取，原文件已保留。重新保存设置会先备份。${String(error)}`;
        }
    }

    get error(): string | null {
        return [this.progressError, this.settingsError].filter(Boolean).join('\n') || null;
    }

    private validateTermIDs(progress: ProgressData): void {
        const ids = [...progress.favoriteIDs, ...progress.wrongIDs, ...progress.masteredIDs,
            ...Object.keys(progress.correctStreak).map(Number),
            ...progress.attempts.flatMap(a => [a.termID, a.selectedOptionID, a.correctOptionID])];
        if (ids.some(id => !this.termIDs.has(id))) throw new Error('记录包含词库中不存在的编号。');
    }

    private save(next: ProgressData): ProgressData {
        if (this.progressBlocked) throw new Error(this.error ?? '学习记录无法读取，尚未覆盖原文件。');
        this.validateTermIDs(next);
        atomicWrite(this.progressFile, next);
        this.progress = next;
        this.progressError = null;
        return next;
    }

    favorite(id: number): ProgressData {
        if (!this.termIDs.has(id)) throw new Error('无效词汇编号。');
        return this.save(toggleFavorite(this.progress, id));
    }

    preferences(input: unknown): ProgressData {
        const preferences: QuizPreferences = validatePreferences(input);
        return this.save({ ...this.progress, quizPreferences: preferences });
    }

    answer(input: unknown): ProgressData {
        const attempt = decodeProgress({ attempts: [input] }).attempts[0] as QuizAttempt | undefined;
        if (!attempt || attempt.termID !== attempt.correctOptionID) throw new Error('无效答题记录。');
        if (this.progress.attempts.some(a => a.id === attempt.id)) return this.progress;
        return this.save(recordAttempt(this.progress, attempt));
    }

    reset(): ProgressData {
        if (this.progressBlocked && existsSync(this.progressFile)) {
            copyFileSync(this.progressFile, `${this.progressFile}.backup-${Date.now()}`);
        }
        const next = { ...defaultProgress(), quizPreferences: this.progress.quizPreferences };
        // 原子写入成功前不解除损坏记录保护。
        atomicWrite(this.progressFile, next);
        this.progressBlocked = false;
        this.progress = next;
        this.progressError = null;
        return next;
    }

    setSettings(input: unknown): SpeechSettings {
        const next = validateSettings(input);
        if (this.settingsBlocked && existsSync(this.settingsFile)) {
            copyFileSync(this.settingsFile, `${this.settingsFile}.backup-${Date.now()}`);
        }
        atomicWrite(this.settingsFile, next);
        this.settingsBlocked = false;
        this.settings = next;
        this.settingsError = null;
        return next;
    }
}
