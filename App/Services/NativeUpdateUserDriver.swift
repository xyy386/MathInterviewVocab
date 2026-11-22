import Foundation
import Sparkle

/** 保留全部 Sparkle 原生界面，只记录公开的取消入口供应用内“暂停”使用。 */
@MainActor
final class NativeUpdateUserDriver: SPUStandardUserDriver {
    var downloadCancellation: (() -> Void)?
    override func showDownloadInitiated(cancellation: @escaping () -> Void) {
        downloadCancellation = cancellation
        super.showDownloadInitiated(cancellation: cancellation)
    }
    override func showDownloadDidStartExtractingUpdate() {
        downloadCancellation = nil
        super.showDownloadDidStartExtractingUpdate()
    }
}
