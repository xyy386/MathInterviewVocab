import { useUpdate, updateBytes, updatePercent, updateSpeed } from './useUpdate';

export function SoftwareUpdate() {
  const { update, error, perform } = useUpdate();
  const busy = update?.status === 'checking' || update?.status === 'downloading' || update?.status === 'verifying' || update?.status === 'installing';
  const downloading = update?.status === 'downloading';
  const percent = update ? updatePercent(update) : 0;

  return <section className="settings-section" aria-labelledby="update-section-title" data-testid="software-update">
    <h2 id="update-section-title">软件更新</h2>
    <div className="settings-group">
      <div className="settings-row"><span>当前版本</span><span className="settings-value update-version">{update?.currentVersion ?? '读取中…'}</span></div>
      <div className="update-route-control" role="group" aria-label="更新线路">
        {([['auto', '智能多线加速'], ['mirror', '加速镜像优先'], ['direct', 'GitHub 直连']] as const).map(([mode, label]) =>
          <button key={mode} type="button" className={update?.routeMode === mode || (!update?.routeMode && mode === 'auto') ? 'is-selected' : ''}
            disabled={!update || busy} aria-pressed={update?.routeMode === mode} onClick={() => void perform(() => window.vocab.setUpdateRouteMode(mode))}>{label}</button>)}
      </div>
      <div className="settings-row update-actions">
        <button type="button" className="secondary-button" disabled={!update || busy} onClick={() => void perform(() => window.vocab.checkForUpdates())}>检查更新</button>
        {update?.canDownload && <button type="button" className="primary-button" disabled={busy} onClick={() => void perform(() => window.vocab.downloadUpdate())}>{update.canResume ? '继续下载' : '下载更新'}</button>}
        {downloading && <button type="button" className="secondary-button" onClick={() => void perform(() => window.vocab.pauseUpdate())}>暂停下载</button>}
        {update?.hasCachedDownload && !busy && <button type="button" className="secondary-button" onClick={() => void perform(() => window.vocab.clearUpdateCache())}>清除下载缓存</button>}
        {update?.status === 'ready' && <>
          <button type="button" className="primary-button" onClick={() => void perform(() => window.vocab.installUpdate())}>安装并重启</button>
          <button type="button" className="secondary-button" onClick={() => void perform(() => window.vocab.showUpdateDownload())}>在文件夹中显示</button>
        </>}
        {update?.version && <button type="button" className="update-release-link" onClick={() => void perform(() => window.vocab.showUpdateRelease())}>查看发布说明</button>}
      </div>
      <p className={`settings-footnote update-status ${update?.status === 'error' || error ? 'settings-error' : ''}`} role={update?.status === 'error' || error ? 'alert' : 'status'}>{error || update?.message || '正在读取版本信息…'}</p>
      {(downloading || update?.status === 'paused' || update?.status === 'verifying') && update && <>
        <div className="update-progress"><progress value={percent} max={100} aria-label="更新下载进度"/><span>{percent}%</span></div>
        <p className="settings-footnote update-transfer-detail">{updateBytes(update.downloadedBytes ?? 0)} / {updateBytes(update.totalBytes ?? 0)} · {updateSpeed(update.speedBytesPerSecond ?? 0)}{update.activeRouteLabel ? ` · ${update.activeRouteLabel}` : ''}</p>
      </>}
      {update?.activeRouteLabel && !downloading && <p className="settings-footnote">本次线路：{update.activeRouteLabel}</p>}
      {update?.notes && <details className="update-notes"><summary>版本 {update.version} 更新内容</summary><p>{update.notes}</p></details>}
      <p className="settings-footnote">词库与发音始终离线；仅检查和下载更新时联网。下载并验证数字签名后，点击“安装并重启”即可更新，无需手动解压、退出或覆盖文件。学习记录会保留。</p>
      <p className="settings-footnote">智能或镜像模式会连接第三方加速节点，仅请求公开更新文件；可选择 GitHub 直连。暂停或退出会保留断点，下次由你手动继续，不在退出后常驻下载。</p>
    </div>
  </section>;
}
