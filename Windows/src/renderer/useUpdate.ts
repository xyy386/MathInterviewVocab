import { useEffect, useState } from 'react';
import type { UpdateState } from '../shared/update';

export function useUpdate() {
  const [update, setUpdate] = useState<UpdateState | null>(null);
  const [error, setError] = useState('');
  useEffect(() => {
    if (typeof window.vocab?.getUpdateState !== 'function') return;
    let active = true;
    const unsubscribe = window.vocab.onUpdateChanged(value => { if (active) setUpdate(value); });
    window.vocab.getUpdateState().then(value => { if (active) setUpdate(value); })
      .catch(() => { if (active) setError('无法读取更新信息，请重新打开设置。'); });
    return () => { active = false; unsubscribe(); };
  }, []);
  async function perform(operation: () => Promise<unknown>) {
    setError('');
    try { await operation(); }
    catch { setError('更新操作失败，请检查网络或缓存目录权限后重试。'); }
  }
  return { update, error, perform };
}
export function updatePercent(update: UpdateState): number {
  return update.totalBytes ? Math.max(0, Math.min(100, Math.floor((update.downloadedBytes ?? 0) / update.totalBytes * 100))) : 0;
}
export function updateBytes(bytes: number): string { return `${(bytes / 1048576).toFixed(1)} MB`; }
export function updateSpeed(bytes: number): string {
  return bytes >= 1048576 ? `${(bytes / 1048576).toFixed(1)} MB/s` : `${Math.max(0, Math.floor(bytes / 1024))} KB/s`;
}
