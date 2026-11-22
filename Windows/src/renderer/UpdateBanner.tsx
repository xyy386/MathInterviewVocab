import { useUpdate, updateBytes, updatePercent, updateSpeed } from './useUpdate';

export function UpdateBanner() {
  const { update, error, perform } = useUpdate();
  if (!update || !['downloading', 'verifying', 'paused', 'ready', 'error'].includes(update.status)
    || (update.status === 'error' && !update.hasCachedDownload)) return null;
  const downloading = update.status === 'downloading';
  const ready = update.status === 'ready';
  const title = ready ? `版本 ${update.version ?? ''} 已就绪` : update.status === 'verifying' ? '正在校验更新'
    : downloading ? `后台下载 ${update.version ?? ''}` : update.status === 'paused' ? '更新下载已暂停' : '更新需要处理';
  return <aside className="update-banner" aria-label="后台更新进度" data-testid="update-banner">
    <div className="update-banner-info"><span className="update-banner-title">{title}</span>
      <span className="update-banner-detail">{updatePercent(update)}% · {updateBytes(update.downloadedBytes ?? 0)} / {updateBytes(update.totalBytes ?? 0)}
        {downloading ? ` · ${updateSpeed(update.speedBytesPerSecond ?? 0)}` : ''}{update.activeRouteLabel ? ` · ${update.activeRouteLabel}` : ''}</span>
      {error && <span className="settings-error" role="alert">{error}</span>}
    </div>
    <div className="update-banner-actions">
      {downloading && <button type="button" className="borderless-button" onClick={() => void perform(() => window.vocab.pauseUpdate())}>暂停</button>}
      {update.canResume && !downloading && update.status !== 'verifying' && <button type="button" className="borderless-button" onClick={() => void perform(() => window.vocab.downloadUpdate())}>继续</button>}
      <button type="button" className="borderless-button" onClick={() => void perform(() => window.vocab.openSettings())}>查看</button>
      {ready && <button type="button" className="primary-button" onClick={() => void perform(() => window.vocab.installUpdate())}>安装并重启</button>}
    </div>
    <progress value={updatePercent(update)} max={100} aria-label="后台更新下载进度"/>
  </aside>;
}
