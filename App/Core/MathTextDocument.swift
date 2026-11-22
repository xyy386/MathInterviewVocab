import Foundation

struct MathTextSegment: Equatable, Sendable {
    let text: String
    let latex: String?
    let display: Bool
}

struct MathTextDocument: Equatable, Sendable {
    let segments: [MathTextSegment]

    init(_ source: String) {
        let string = source as NSString
        let pattern = try! NSRegularExpression(pattern: #"\\\(([\s\S]*?)\\\)|\\\[([\s\S]*?)\\\]"#)
        var result: [MathTextSegment] = []; var end = 0
        for match in pattern.matches(in: source, range: NSRange(location: 0, length: string.length)) {
            if match.range.location > end {
                result.append(.init(text: string.substring(with: NSRange(location: end, length: match.range.location - end)).replacingOccurrences(of: "`", with: ""), latex: nil, display: false))
            }
            let display = match.range(at: 2).location != NSNotFound
            result.append(.init(text: string.substring(with: match.range), latex: string.substring(with: match.range(at: display ? 2 : 1)), display: display))
            end = NSMaxRange(match.range)
        }
        if end < string.length { result.append(.init(text: string.substring(from: end).replacingOccurrences(of: "`", with: ""), latex: nil, display: false)) }
        segments = result
    }
    var containsMath: Bool { segments.contains { $0.latex != nil } }

    static func searchText(_ source: String) -> String {
        source.precomposedStringWithCompatibilityMapping
            .replacingOccurrences(of: #"\\[()[\]{}]"#, with: "", options: .regularExpression)
            .replacingOccurrences(of: #"[_^{}]"#, with: "", options: .regularExpression).lowercased()
    }
}
