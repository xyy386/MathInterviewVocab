import AppKit
import CryptoKit
import SwiftUI
import Testing
@testable import MathInterviewVocab

@Suite(.serialized)
@MainActor
struct MathTextTests {
    @Test func allPublishedFormulaResourcesAreCompleteAndVectorPDFs() throws {
        let resources = MathAssets.shared
        #expect(resources.entries.count == 63)
        #expect(try #require(resources.entries.first { $0.tex.hasPrefix("F(x)=\\int") }).widthEm > 8)
        for asset in resources.entries {
            let page = try #require(resources.page(for: asset))
            #expect(page.getBoxRect(.mediaBox).width > 0)
            #expect(asset.heightEm > asset.depthEm)
            #expect(asset.widthEm > 0)
        }
    }
    @Test func mixedFormulaSelectionCopiesOriginalLaTeXAndKeepsUnicodeText() throws {
        let view = PronunciationTextView()
        view.installMathSource("中文🙂 H \\(H_0\\) 与 \\(H_a\\)。", font: .systemFont(ofSize: 16), color: .labelColor)
        #expect(view.string.contains("\u{fffc}"))
        #expect(!view.string.contains("H_0"))
        #expect(view.mathSelection(NSRange(location: 0, length: (view.string as NSString).length)) == "中文🙂 H \\(H_0\\) 与 \\(H_a\\)。")
        let position = (view.string as NSString).range(of: "\u{fffc}")
        #expect(view.mathSelection(position) == "\\(H_0\\)")
        #expect(view.mathSelection(NSRange(location: NSNotFound, length: 1)).isEmpty)
        let board = NSPasteboard.withUniqueName(); defer { board.releaseGlobally() }
        view.copyPasteboard = board; view.setSelectedRange(position); view.copy(nil)
        #expect(board.string(forType: .string) == "\\(H_0\\)")
    }
    @Test func missingFormulaResourcesStayVisibleAndKeepTheirSource() {
        let view = PronunciationTextView()
        view.installMathSource("测试 \\(unbundled_{formula}\\)。", font: .systemFont(ofSize: 16), color: .labelColor)
        #expect(view.string.contains("公式显示异常"))
        #expect(view.string.contains("unbundled_{formula}"))
    }
    @Test(arguments: ["H_0", "H₀", "H0"]) func subscriptSearchAliasesMatch(_ text: String) {
        #expect(MathTextDocument.searchText("Null Hypothesis（\\(H_0\\)）").contains(MathTextDocument.searchText(text)))
    }
    @Test func nativeInlineFormulaBaselineAndCopyGate() throws {
        let source = "原假设 \\(H_0\\)；求和 \\(\\sum_{n=1}^{\\infty}1/n^p\\)；积分 \\(F(x)=\\int_{-\\infty}^{x}f(t)\\,\\mathrm{d}t\\)。"
        let host = NSHostingView(rootView: SelectablePronunciationText(text: source, selectedText: .constant(""),
            font: .systemFont(ofSize: 24), color: .black) { _ in }.frame(width: 820).background(Color.white))
        host.frame = NSRect(x: 0, y: 0, width: 820, height: 160)
        let window = NSWindow(contentRect: host.frame, styleMask: [.titled], backing: .buffered, defer: false)
        window.isReleasedWhenClosed = false; defer { window.close() }
        window.contentView = host; window.orderFront(nil)
        RunLoop.current.run(until: Date(timeIntervalSinceNow: 0.1))
        host.layoutSubtreeIfNeeded(); host.displayIfNeeded()
        #expect(host.fittingSize.height >= 28)
        #expect(host.fittingSize.height < 160)
        if let directory = ProcessInfo.processInfo.environment["MATH_VOCAB_VALIDATION_DIRECTORY"],
           let bitmap = host.bitmapImageRepForCachingDisplay(in: host.bounds) {
            host.cacheDisplay(in: host.bounds, to: bitmap)
            try bitmap.representation(using: .png, properties: [:])?.write(to: URL(fileURLWithPath: directory).appendingPathComponent("mac-native-math-gate.png"))
        }
    }

    @Test func nativeMatrixAndOversizedFormulaKeepTheirNaturalSizeAndCopySource() throws {
        let directory = try #require(Bundle(for: MathTestBundleMarker.self).resourceURL)
        let assets = MathAssets(directory: directory)
        let asset = try #require(assets.entries.first { $0.tex.hasPrefix("\\begin{pmatrix}") })
        let source = "矩阵 \\[\(asset.tex)\\]。"
        let view = PronunciationTextView(frame: NSRect(x: 0, y: 0, width: 100, height: 200))
        view.mathAssets = assets; view.isEditable = false; view.drawsBackground = true; view.backgroundColor = .white
        view.textContainerInset = .zero; view.textContainer?.lineFragmentPadding = 0
        view.installMathSource(source, font: .systemFont(ofSize: 40), color: .black)
        view.updateMathLayout(width: 100)
        view.textContainer?.containerSize = NSSize(width: 100, height: 1000)
        view.layoutSubtreeIfNeeded()
        #expect(!view.string.contains("公式显示异常"))
        #expect(view.mathSelection(NSRange(location: 0, length: (view.string as NSString).length)) == source)
        var cells: [MathPDFCell] = []
        view.textStorage?.enumerateAttribute(.attachment, in: NSRange(location: 0, length: (view.string as NSString).length)) { value, _, _ in
            if let cell = (value as? NSTextAttachment)?.attachmentCell as? MathPDFCell { cells.append(cell) }
        }
        let cell = try #require(cells.first)
        #expect(cell.overflows); #expect(cell.naturalSize.width > cell.cellSize().width)
        #expect(view.subviews.contains { $0 is NSScrollView })
    }

    @Test func allComplexFixturesKeepVerifiedPagesAndOriginalCopySource() throws {
        let assets = MathAssets(directory: try #require(Bundle(for: MathTestBundleMarker.self).resourceURL))
        #expect(assets.entries.count == 6)
        for asset in assets.entries {
            #expect(assets.page(for: asset) != nil)
            let source = "前文 \\[\(asset.tex)\\] 后文"
            let view = PronunciationTextView()
            view.mathAssets = assets
            view.installMathSource(source, font: .systemFont(ofSize: 32), color: .labelColor)
            #expect(view.mathSelection(NSRange(location: 0, length: (view.string as NSString).length)) == source)
        }
    }

    @Test(arguments: [1000.0, 1220.0, 1440.0])
    func formulaColumnsFitBothSidebarWidthsAndDoubleFontSize(_ windowWidth: Double) throws {
        let source = "维数公式 \\(\\dim(U+W)=\\dim U+\\dim W-\\dim(U\\cap W)\\)；积分 \\(F(x)=\\int_{-\\infty}^{x}f(t)\\,\\mathrm{d}t\\)。"
        for sidebarWidth in [0.0, 380.0] {
            let width = min(820, windowWidth - sidebarWidth - 60)
            for fontSize in [16.0, 32.0] {
                let host = NSHostingView(rootView: RichMathText(source: source, size: fontSize).frame(width: width).background(Color.white))
                host.frame = NSRect(x: 0, y: 0, width: width, height: 240)
                let window = NSWindow(contentRect: host.frame, styleMask: [.titled], backing: .buffered, defer: false)
                window.isReleasedWhenClosed = false
                window.contentView = host; window.orderFront(nil)
                RunLoop.current.run(until: Date(timeIntervalSinceNow: 0.03))
                host.layoutSubtreeIfNeeded(); host.displayIfNeeded()
                #expect(host.fittingSize.width <= width + 1)
                #expect(host.fittingSize.height > 16 && host.fittingSize.height < 240)
                if fontSize == 32, let directory = ProcessInfo.processInfo.environment["MATH_VOCAB_VALIDATION_DIRECTORY"],
                   let bitmap = host.bitmapImageRepForCachingDisplay(in: host.bounds) {
                    host.cacheDisplay(in: host.bounds, to: bitmap)
                    try bitmap.representation(using: .png, properties: [:])?.write(to: URL(fileURLWithPath: directory).appendingPathComponent("mac-formula-\(Int(windowWidth))-sidebar\(Int(sidebarWidth)).png"))
                }
                window.close()
            }
        }
    }
}

private final class MathTestBundleMarker: NSObject {}
