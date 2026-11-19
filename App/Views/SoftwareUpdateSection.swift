import SwiftUI

struct SoftwareUpdateSection: View {
    @Environment(UpdateService.self) private var updates

    var body: some View {
        Section("软件更新") {
            LabeledContent("当前版本", value: updates.currentVersion)

            HStack(spacing: 10) {
                Button(updates.isBusy ? "显示更新窗口" : "检查更新") { updates.checkForUpdates() }
                    .disabled(!updates.canCheckForUpdates)

                if updates.canRetryInstallation {
                    Button("重试安装并重启") { updates.retryInstallation() }
                }

                if updates.isBusy {
                    ProgressView().controlSize(.small)
                }
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
        }
    }

    private var isFailure: Bool {
        if case .failed = updates.phase { return true }
        return false
    }

    private var status: String {
        switch updates.phase {
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
