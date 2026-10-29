import { useCallback, useEffect, useRef, useState } from 'react';
import { sanitizeSelection, type Accent, type SpeechSettings } from '../shared/core';

export interface PlaybackState {
  kind: 'idle' | 'loading' | 'playing' | 'missing' | 'error';
  message: string;
}
export interface AudioController {
  state: PlaybackState;
  play: (text: string, accent?: Accent) => Promise<void>;
  stop: () => void;
}
const idle: PlaybackState = { kind: 'idle', message: '' };

export function useAudio(settings: SpeechSettings): AudioController {
  const player = useRef<HTMLAudioElement | null>(null);
  const request = useRef(0);
  const [state, setState] = useState<PlaybackState>(idle);
  const stop = useCallback(() => {
    request.current += 1;
    if (player.current) {
      player.current.pause(); player.current.removeAttribute('src'); player.current.load(); player.current = null;
    }
    setState(idle);
  }, []);
  const play = useCallback(async (input: string, accent = settings.accent) => {
    stop();
    const text = sanitizeSelection(input);
    if (!text) { setState({ kind: 'missing', message: '请先选择包含字母或数字的英文。' }); return; }
    const token = request.current;
    const name = accent === 'en-US' ? '美式' : '英式';
    setState({ kind: 'loading', message: `正在准备${name}发音…` });
    try {
      const result = await window.vocab.getAudio(text, accent);
      if (request.current !== token) return;
      if (result.status !== 'ready' || !result.url) { setState({ kind: 'missing', message: `内置语音中暂未找到“${text}”。` }); return; }
      const audio = new Audio(result.url);
      audio.playbackRate = settings.speed; audio.preservesPitch = true;
      audio.onended = () => { if (request.current === token) setState(idle); };
      audio.onerror = () => { if (request.current === token) setState({ kind: 'error', message: '音频文件无法播放，请检查离线语音资源。' }); };
      player.current = audio;
      await audio.play();
      if (request.current === token) setState({ kind: 'playing', message: `${name}发音 · ${settings.speed}×` });
    } catch (error) {
      if (request.current === token) setState({ kind: 'error', message: error instanceof Error ? error.message : '读取离线音频失败。' });
    }
  }, [settings.accent, settings.speed, stop]);
  useEffect(() => stop, [stop]);
  useEffect(() => window.vocab?.onStopAudio(stop), [stop]);
  useEffect(() => { if (player.current) player.current.playbackRate = settings.speed; }, [settings.speed]);
  useEffect(() => window.vocab?.onSelectionSpeak(text => { void play(text); }), [play]);
  return { state, play, stop };
}
