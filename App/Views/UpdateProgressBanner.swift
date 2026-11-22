import SwiftUI

func updateTransferDetail(_ state: MacTransferState) -> String {
    let downloaded = String(format: "%.1f", Double(state.downloadedBytes) / 1_048_576)
    let total = String(format: "%.1f", Double(state.totalBytes) / 1_048_576)
    let speed = state.speedBytesPerSecond >= 1_048_576
        ? String(format: "%.1f MB/s", Double(state.speedBytesPerSecond) / 1_048_576)
        : "\(max(0, state.speedBytesPerSecond / 1024)) KB/s"
    return ["\(state.percent)% · \(downloaded) / \(total) MB", state.status == "downloading" ? speed : "", state.activeRouteLabel]
        .filter { !$0.isEmpty }.joined(separator: " · ")
}

struct UpdateProgressBanner: View {
    @Environment(UpdateService.self) private var updates
    @Environment(\.openSettings) private var openSettings
    private var state: MacTransferState { updates.transfer }
    var body: some View {
        UpdateProgressContent(state: state, busy: updates.isBusy,
            pause: { updates.pauseDownload() }, resume: { updates.resumeDownload() },
            view: { if !updates.showExistingUpdateWindow() { openSettings() } })
    }
}

struct UpdateProgressContent: View {
    let state: MacTransferState
    let busy: Bool
    let pause: () -> Void
    let resume: () -> Void
    let view: () -> Void
    private var visible: Bool {
        ["downloading", "paused", "verifying", "ready"].contains(state.status)
            || (state.status == "error" && state.hasCachedDownload)
    }
    var body: some View {
        if visible {
            VStack(spacing: 7) {
                HStack(spacing: 14) {
                    VStack(alignment: .leading, spacing: 3) {
                        Text(title).font(.caption.weight(.semibold)).foregroundStyle(AcademicTheme.formulaBlue)
                        Text(updateTransferDetail(state)).font(.caption.monospacedDigit()).foregroundStyle(.secondary)
                            .fixedSize(horizontal: false, vertical: true)
                    }
                    Spacer(minLength: 8)
                    if state.status == "downloading" { Button("暂停", action: pause).buttonStyle(.borderless) }
                    if state.canResume && !busy { Button("继续", action: resume).buttonStyle(.borderless) }
                    if state.status == "ready" {
                        Button("安装并重启…", action: resume).buttonStyle(.borderless)
                    } else {
                        Button("查看", action: view).buttonStyle(.borderless)
                    }
                }
                .padding(.horizontal, 22).padding(.top, 8)
                ProgressView(value: Double(state.downloadedBytes), total: Double(max(1, state.totalBytes)))
                    .tint(AcademicTheme.formulaBlue).controlSize(.mini).accessibilityLabel("后台更新下载进度")
            }
            .padding(.bottom, 2)
            .background(AcademicTheme.paperWhite)
            .overlay(alignment: .bottom) { Rectangle().fill(AcademicTheme.gridBlue).frame(height: 1) }
            .accessibilityElement(children: .contain).accessibilityLabel("后台更新进度")
        }
    }
    private var title: String {
        switch state.status {
        case "ready": "版本 \(state.version ?? "") 已就绪"
        case "paused": "更新下载已暂停"
        case "verifying": "正在校验更新"
        case "error": "更新需要处理：\(state.message)"
        default: "后台下载 \(state.version ?? "")"
        }
    }
}
