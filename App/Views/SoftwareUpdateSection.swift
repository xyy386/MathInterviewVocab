import SwiftUI

struct SoftwareUpdateSection: View {
    @Environment(UpdateService.self) private var updates

    var body: some View {
        Section("软件更新") {
            LabeledContent("当前版本", value: updates.currentVersion)

            Picker("更新线路", selection: Binding(get: { updates.transfer.routeMode }, set: { updates.setRouteMode($0) })) {
                ForEach(UpdateRouteMode.allCases) { mode in Text(mode.title).tag(mode) }
            }
            .pickerStyle(.segmented)
            .disabled(updates.isBusy)

            HStack(spacing: 10) {
                Button(updates.isBusy ? "显示更新窗口" : "检查更新") { updates.checkForUpdates() }
                    .disabled(!updates.canCheckForUpdates && !updates.nativeSessionActive)

                if updates.canRetryInstallation {
                    Button("重试安装并重启") { updates.retryInstallation() }
                }

                if updates.transfer.canResume && !updates.isBusy {
                    Button("继续下载…") { updates.resumeDownload() }
                }
                if updates.transfer.status == "downloading" {
                    Button("暂停下载") { updates.pauseDownload() }
                }

                if updates.isBusy {
                    ProgressView().controlSize(.small)
                }
            }

            if updates.transfer.hasCachedDownload && !updates.isBusy {
                Button("清除下载缓存…") { updates.clearDownloadCache() }
            }

            if ["downloading", "paused", "verifying", "ready"].contains(updates.transfer.status) {
                ProgressView(value: Double(updates.transfer.downloadedBytes), total: Double(max(1, updates.transfer.totalBytes)))
                    .accessibilityLabel("更新下载进度")
                Text(updateTransferDetail(updates.transfer))
                    .font(.caption.monospacedDigit()).foregroundStyle(.secondary)
                    .fixedSize(horizontal: false, vertical: true)
            }

            Text(status)
                .font(.caption)
                .foregroundStyle(isFailure ? AcademicTheme.errorRed : .secondary)
                .accessibilityLabel(status)

            Text("检查更新后会打开安装窗口，可查看说明、下载或取消。签名校验通过并经你确认后，应用会保存学习记录，退出、替换并重新打开，无需手动解压覆盖。")
                .font(.caption)
                .foregroundStyle(.secondary)
                .fixedSize(horizontal: false, vertical: true)

            Text("词库与发音始终离线；仅主动检查和下载安装包时联网。不会自动检查、自动安装或上传学习记录；系统权限和安全提示仍会保留。")
                .font(.caption)
                .foregroundStyle(.secondary)
                .fixedSize(horizontal: false, vertical: true)

            Text("智能或镜像模式会请求第三方加速节点，可切换 GitHub 直连。取消、暂停或退出会保留断点，下次手动继续；不会退出后常驻下载。")
                .font(.caption).foregroundStyle(.secondary).fixedSize(horizontal: false, vertical: true)
        }
    }

    private var isFailure: Bool {
        if case .failed = updates.phase { return true }
        return updates.transfer.status == "error"
    }

    private var status: String {
        if updates.preparing || updates.transfer.status == "paused" || updates.transfer.status == "error" {
            return updates.transfer.message
        }
        return switch updates.phase {
        case .idle: "手动检查 GitHub 正式版，不会在后台自动联网。"
        case .checking: "正在检查更新…"
        case .current: "当前已是最新版本。"
        case .available(let version): "发现新版本 \(version)，请在更新窗口中确认。"
        case .downloading: "正在下载更新，可在更新窗口中查看进度或取消。"
        case .verifying: "正在验证更新签名并准备安装…"
        case .ready: "更新已准备好，请在更新窗口中选择安装并重启。"
        case .installing: "正在安全保存学习记录并安装更新…"
        case .deferred: "本次更新已结束或稍后安装；当前版本仍可使用。"
        case .failed(let message): message
        }
    }
}

struct CheckForUpdatesCommand: View {
    let updates: UpdateService

    var body: some View {
        Button("检查更新…") {
            updates.checkForUpdates()
        }
        .disabled(!updates.canCheckForUpdates)
    }
}
