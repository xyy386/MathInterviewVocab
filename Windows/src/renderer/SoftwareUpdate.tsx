import { useEffect, useState } from 'react';
import type { UpdateState } from '../shared/update';

export function SoftwareUpdate() {
  const [update, setUpdate] = useState<UpdateState | null>(null);
  const [error, setError] = useState('');

  useEffect(() => {
    let active = true;
    const unsubscribe = window.vocab.onUpdateChanged(value => { if (active) setUpdate(value); });
    window.vocab.getUpdateState().then(value => { if (active) setUpdate(value); })
      .catch(() => { if (active) setError('无法读取版本信息，请重新打开设置。'); });
    return () => { active = false; unsubscribe(); };
  }, []);

  async function perform(operation: () => Promise<unknown>) {
    setError('');
    try { await operation(); }
    catch { setError('更新操作失败，请检查网络或下载目录权限后重试。'); }
  }

  const busy = update?.status === 'checking' || update?.status === 'downloading';
  const downloading = update?.status === 'downloading';
  const percent = update?.totalBytes ? Math.min(100, Math.floor((update.downloadedBytes ?? 0) / update.totalBytes * 100)) : 0;

  return <section className="settings-section" aria-labelledby="update-section-title" data-testid="software-update">
    <h2 id="update-section-title">软件更新</h2>
    <div className="settings-group">
      <div className="settings-row"><span>当前版本</span><span className="settings-value update-version">{update?.currentVersion ?? '读取中…'}</span></div>
      <div className="settings-row update-actions">
        <button type="button" className="secondary-button" disabled={!update || busy} onClick={() => void perform(() => window.vocab.checkForUpdates())}>检查更新</button>
        {update?.canDownload && <button type="button" className="primary-button" onClick={() => void perform(() => window.vocab.downloadUpdate())}>下载更新</button>}
        {downloading && <button type="button" className="secondary-button" onClick={() => void perform(() => window.vocab.cancelUpdate())}>取消下载</button>}
        {update?.status === 'ready' && <button type="button" className="secondary-button" onClick={() => void perform(() => window.vocab.showUpdateDownload())}>在文件夹中显示</button>}
        {update?.version && <button type="button" className="update-release-link" onClick={() => void perform(() => window.vocab.showUpdateRelease())}>查看发布说明</button>}
      </div>
      <p className={`settings-footnote update-status ${update?.status === 'error' || error ? 'settings-error' : ''}`} role={update?.status === 'error' || error ? 'alert' : 'status'}>{error || update?.message || '正在读取版本信息…'}</p>
      {downloading && <div className="update-progress"><progress value={percent} max={100} aria-label="更新下载进度"/><span>{percent}%</span></div>}
      {update?.notes && <details className="update-notes"><summary>版本 {update.version} 更新内容</summary><p>{update.notes}</p></details>}
      <p className="settings-footnote">词库与发音始终离线；仅检查和下载更新时联网。下载校验完成后，请退出旧版并用新版替换，学习记录会保留。</p>
    </div>
  </section>;
}
