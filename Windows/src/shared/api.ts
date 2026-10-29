import type {
  Accent,
  ProgressData,
  QuizAttempt,
  QuizPreferences,
  SpeechSettings,
  Term,
} from './core';

export interface AudioInfo {
  clips: number;
  texts: number;
  source: string;
}

export interface LoadPayload {
  terms: Term[];
  progress: ProgressData;
  settings: SpeechSettings;
  storagePath: string;
  audioInfo: AudioInfo;
  persistenceError?: string | null;
}

export interface AudioResult {
  url: string | null;
  status: 'ready' | 'missing';
  matchedText?: string;
}

export interface DataChange {
  progress?: ProgressData;
  settings?: SpeechSettings;
  persistenceError?: string | null;
}

export interface VocabAPI {
  load(): Promise<LoadPayload>;
  toggleFavorite(id: number): Promise<ProgressData>;
  updateQuizPreferences(preferences: QuizPreferences): Promise<ProgressData>;
  recordAnswer(attempt: QuizAttempt): Promise<ProgressData>;
  resetProgress(): Promise<ProgressData>;
  saveSettings(settings: SpeechSettings): Promise<SpeechSettings>;
  getAudio(text: string, accent: Accent): Promise<AudioResult>;
  showSelectionMenu(text: string): Promise<void>;
  onSelectionSpeak(callback: (text: string) => void): () => void;
  onStopAudio(callback: () => void): () => void;
  onDataChanged(callback: (change: DataChange) => void): () => void;
  openSettings(): Promise<void>;
  confirmReset(): Promise<boolean>;
  windowAction(action: 'close' | 'minimize' | 'maximize'): Promise<void>;
}

declare global {
  interface Window {
    vocab: VocabAPI;
  }
}

export {};
