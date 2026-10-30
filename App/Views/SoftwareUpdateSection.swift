import SwiftUI

struct SoftwareUpdateSection: View {
    @Environment(UpdateService.self) private var updates

    var body: some View {
        Section("软件更新") {
            LabeledContent("当前版本", value: updates.currentVersion)

            HStack(spacing: 10) {
                Button("检查更新") { updates.checkForUpdates() }
                    .disabled(updates.isBusy)

                if updates.phase == .downloading {
                    Button("取消下载") { updates.cancelDownload() }
                } else if updates.phase == .ready {
                    Button("在文件夹中显示") { updates.showDownloadedArchive() }
                } else if updates.release != nil {
                    Button("下载更新") { updates.downloadUpdate() }
                        .disabled(updates.isBusy)
                }

                if updates.isBusy {
                    ProgressView().controlSize(.small)
                }
            }

            Text(status)
                .font(.caption)
                .foregroundStyle(isFailure ? AcademicTheme.errorRed : .secondary)
                .accessibilityLabel(status)

            if let release = updates.release {
                Button("查看发布说明") { updates.showReleaseNotes() }
                DisclosureGroup("更新说明") {
                    ScrollView {
                        Text(verbatim: release.notes)
                            .font(.caption)
                            .frame(maxWidth: .infinity, alignment: .leading)
                    }
                    .frame(maxHeight: 120)
                }
            }

            Text("词库与发音始终离线；仅检查和下载更新时联网。下载校验完成后，请退出旧版并用新版替换，学习记录会保留。")
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
        case .available: "发现新版本 \(updates.release?.version.description ?? "")。"
        case .downloading: "正在下载并校验更新包…"
        case .ready: "下载完成，SHA-256 校验通过。请退出旧版，解压后用新版替换。"
        case .failed(let message): message
        }
    }
}

struct CheckForUpdatesCommand: View {
    let updates: UpdateService
    @Environment(\.openSettings) private var openSettings

    var body: some View {
        Button("检查更新…") {
            openSettings()
            updates.checkForUpdates()
        }
        .disabled(updates.isBusy)
    }
}
