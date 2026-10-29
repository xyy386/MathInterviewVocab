import { useEffect, useRef, useState } from 'react';

import type { AudioInfo } from '../shared/api';
import type { Accent, ProgressData, SpeechSettings } from '../shared/core';
import { Icon } from './ui';
import type { AudioController } from './useAudio';

interface SettingsViewProps {
  settings: SpeechSettings;
  progress: ProgressData;
  audio: AudioController;
  audioInfo: AudioInfo;
  onSave: (settings: SpeechSettings) => Promise<void>;
  onReset: () => Promise<void>;
  error?: string;
}

const accents: { id: Accent; label: string }[] = [
  { id: 'en-US', label: '美式英语' },
  { id: 'en-GB', label: '英式英语' },
];

export function SettingsView({ settings, audio, audioInfo, onSave, onReset, error }: SettingsViewProps) {
  const [operationError, setOperationError] = useState('');
  const [resetting, setResetting] = useState(false);
  const [displaySettings, setDisplaySettings] = useState(settings);
  const pendingSettings = useRef(settings);
  const confirmedSettings = useRef(settings);
  const saveQueue = useRef(Promise.resolve());
  const saveRevision = useRef(0);
  const pendingCount = useRef(0);

  useEffect(() => {
    if (!pendingCount.current) {
      pendingSettings.current = settings;
      confirmedSettings.current = settings;
      setDisplaySettings(settings);
    }
  }, [settings]);

  useEffect(() => () => audio.stop(), [audio.stop]);

  const saveSettings = (patch: Partial<SpeechSettings>) => {
    const nextSettings = { ...pendingSettings.current, ...patch };
    pendingSettings.current = nextSettings;
    setDisplaySettings(nextSettings);
    const revision = ++saveRevision.current;
    pendingCount.current += 1;
    setOperationError('');
    saveQueue.current = saveQueue.current.catch(() => {}).then(async () => {
      try { await onSave(nextSettings); confirmedSettings.current = nextSettings; }
      catch (saveError) {
        if (revision === saveRevision.current) {
          pendingSettings.current = confirmedSettings.current;
          setDisplaySettings(confirmedSettings.current);
          setOperationError(saveError instanceof Error ? saveError.message : '发音设置保存失败。');
        }
      } finally { pendingCount.current -= 1; }
    });
  };

  const resetProgress = async () => {
    if (resetting) return;
    setOperationError('');
    setResetting(true);
    try {
      const confirmed = await window.vocab.confirmReset();
      if (!confirmed) return;
      audio.stop();
      await onReset();
    } catch (resetError) {
      setOperationError(resetError instanceof Error ? resetError.message : '学习记录清空失败。');
    } finally {
      setResetting(false);
    }
  };

  const displayError = operationError || error;
  const playbackError = audio.state.kind === 'error' || audio.state.kind === 'missing';
  const audioReady = audioInfo.clips > 0 && audioInfo.texts > 0;

  return (
    <main className="settings-form" aria-label="数学面试词汇设置" data-testid="settings-window">
      <section className="settings-section" aria-labelledby="speech-section-title">
        <h2 id="speech-section-title">英语发音</h2>
        <div className="settings-group">
          <div className="settings-row settings-accent-row">
            <span id="default-accent-label">默认口音</span>
            <div className="segmented-control settings-accent" role="group" aria-labelledby="default-accent-label" data-testid="settings-accent">
              {accents.map(accent => (
                <button
                  key={accent.id}
                  type="button"
                  className={displaySettings.accent === accent.id ? 'is-active' : ''}
                  aria-pressed={displaySettings.accent === accent.id}
                  onClick={() => saveSettings({ accent: accent.id })}
                >
                  {accent.label}
                </button>
              ))}
            </div>
          </div>

          <div className="settings-row">
            <label htmlFor="speech-speed">语速</label>
            <select
              id="speech-speed"
              value={displaySettings.speed}
              onChange={event => saveSettings({ speed: Number(event.target.value) })}
              data-testid="settings-speed"
            >
              <option value={0.75}>0.75×</option>
              <option value={1}>1×</option>
              <option value={1.25}>1.25×</option>
            </select>
          </div>

          <div className="settings-row settings-preview-actions">
            <button type="button" className="secondary-button" onClick={() => void audio.play('Mathematical Analysis', 'en-US')}>
              <Icon name="speaker" size={13} />
              试听美式
            </button>
            <button type="button" className="secondary-button" onClick={() => void audio.play('Mathematical Analysis', 'en-GB')}>
              <Icon name="speaker" size={13} />
              试听英式
            </button>
          </div>

          <div className="settings-row" data-testid="audio-source">
            <span>离线语音</span>
            <span className="settings-value" title={audioInfo.source}>Kokoro-82M</span>
          </div>
          <p className="settings-footnote">
            {audioReady
              ? '内置美式与英式自然语音，发音无需联网；语音资源随软件提供。'
              : '离线语音资源未准备完成，请检查随软件提供的语音资源。'}
          </p>
          {playbackError && <p className="settings-footnote settings-error" role="status">{audio.state.message}</p>}
        </div>
      </section>

      <section className="settings-section" aria-labelledby="data-section-title">
        <h2 id="data-section-title">数据</h2>
        <div className="settings-group">
          <div className="settings-row">
            <span>学习记录</span>
            <span className="settings-value">仅保存在当前设备</span>
          </div>
          <div className="settings-row">
            <span>账号</span>
            <span className="settings-value">不需要</span>
          </div>
          <div className="settings-row">
            <span>网络</span>
            <span className="settings-value">不需要</span>
          </div>
          <div className="settings-row settings-reset-row">
            <button type="button" className="danger-text-button" disabled={resetting} onClick={() => void resetProgress()} data-testid="request-reset">
              清空本机学习记录
            </button>
          </div>
        </div>
      </section>

      {displayError && <p className="settings-error settings-save-error" role="alert">本地记录保存失败：{displayError}</p>}
    </main>
  );
}
