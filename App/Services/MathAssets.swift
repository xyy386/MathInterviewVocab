import AppKit
import CryptoKit

struct MathAsset: Codable, Sendable {
    let id: String
    let tex: String
    let display: Bool
    let widthEm: Double
    let heightEm: Double
    let depthEm: Double
    let pdf: String
    let pdfSHA256: String
}
struct MathAssetManifest: Decodable { let schema: Int; let engine: String; let entries: [MathAsset] }

@MainActor
final class MathAssets {
    static let shared = MathAssets()
    let directory: URL?
    private(set) var entries: [MathAsset] = []
    private var pages: [String: CGPDFPage] = [:]
    private var documents: [String: CGPDFDocument] = [:]

    init(directory: URL? = Bundle.main.resourceURL?.appendingPathComponent("Math")) {
        self.directory = directory
        if let directory, let data = try? Data(contentsOf: directory.appendingPathComponent("manifest.json")),
           let manifest = try? JSONDecoder().decode(MathAssetManifest.self, from: data), manifest.schema == 1,
           manifest.engine == "MathJax 4.1.3" { entries = manifest.entries }
    }
    func asset(latex: String, display: Bool) -> MathAsset? { entries.first { $0.tex == latex && $0.display == display } }
    func page(for asset: MathAsset) -> CGPDFPage? {
        if let page = pages[asset.id] { return page }
        guard asset.pdf == asset.id + ".pdf", asset.id.range(of: "^[a-f0-9]{64}$", options: .regularExpression) != nil,
              let directory, let data = try? Data(contentsOf: directory.appendingPathComponent(asset.pdf)),
              SHA256.hash(data: data).map({ String(format: "%02x", $0) }).joined() == asset.pdfSHA256,
              let provider = CGDataProvider(data: data as CFData), let document = CGPDFDocument(provider),
              document.numberOfPages == 1, let page = document.page(at: 1) else { return nil }
        documents[asset.id] = document; pages[asset.id] = page
        return page
    }
}

@MainActor
final class MathPDFCell: NSTextAttachmentCell {
    let page: CGPDFPage
    let color: NSColor
    let size: NSSize
    let naturalSize: NSSize
    let depth: CGFloat
    let source: String

    init(page: CGPDFPage, asset: MathAsset, font: NSFont, color: NSColor, width: CGFloat, source: String) {
        self.page = page; self.color = color; self.source = source
        naturalSize = NSSize(width: asset.widthEm * font.pointSize, height: asset.heightEm * font.pointSize)
        size = NSSize(width: min(width, naturalSize.width), height: naturalSize.height + (naturalSize.width > width ? 16 : 0))
        depth = asset.depthEm * font.pointSize
        super.init(textCell: "")
    }
    required init(coder: NSCoder) { fatalError("Math attachments are read-only generated resources") }
    override func cellSize() -> NSSize { size }
    nonisolated var overflows: Bool { naturalSize.width > size.width + 0.5 }
    override func cellBaselineOffset() -> NSPoint { NSPoint(x: 0, y: -depth - (overflows ? 16 : 0)) }
    override func draw(withFrame cellFrame: NSRect, in controlView: NSView?) {
        guard !overflows else { return }
        drawPDF(in: cellFrame, color: color)
    }
    override func draw(withFrame cellFrame: NSRect, in controlView: NSView?, characterIndex charIndex: Int, layoutManager: NSLayoutManager) {
        // The scroll overlay alone draws an oversized formula; never paint past its viewport.
        guard !overflows else { return }
        let selected = (controlView as? NSTextView).map { NSLocationInRange(charIndex, $0.selectedRange()) } ?? false
        drawPDF(in: NSRect(origin: cellFrame.origin, size: naturalSize), color: selected ? .selectedTextColor : color)
    }
    func drawPDF(in frame: NSRect, color: NSColor) {
        guard let context = NSGraphicsContext.current?.cgContext else { return }
        context.saveGState(); defer { context.restoreGState() }
        context.clip(to: frame); context.beginTransparencyLayer(auxiliaryInfo: nil)
        let box = page.getBoxRect(.mediaBox)
        context.translateBy(x: frame.minX, y: frame.maxY)
        context.scaleBy(x: frame.width / box.width, y: -frame.height / box.height)
        context.drawPDFPage(page)
        context.setBlendMode(.sourceIn); context.setFillColor(color.cgColor); context.fill(box)
        context.endTransparencyLayer()
    }
}
