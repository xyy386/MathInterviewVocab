import Foundation

enum EnglishAccent: String, Codable, CaseIterable, Identifiable, Sendable {
    case american = "en-US"
    case british = "en-GB"

    var id: String { rawValue }

    var shortLabel: String {
        switch self {
        case .american: "US"
        case .british: "UK"
        }
    }

    var chineseName: String {
        switch self {
        case .american: "美式"
        case .british: "英式"
        }
    }

    var fullName: String {
        "\(chineseName)英语"
    }
}

struct TermPronunciation: Codable, Hashable, Sendable {
    let americanIPA: String
    let britishIPA: String

    static let empty = TermPronunciation(americanIPA: "", britishIPA: "")

    func ipa(for accent: EnglishAccent) -> String {
        switch accent {
        case .american: americanIPA
        case .british: britishIPA
        }
    }
}

enum SpeechPlaybackSource: Equatable, Sendable {
    case idle
    case kokoro(EnglishAccent)
    case missing(EnglishAccent, text: String)
    case failed(EnglishAccent)

    var accent: EnglishAccent? {
        switch self {
        case .idle: nil
        case let .kokoro(accent), let .missing(accent, _), let .failed(accent):
            accent
        }
    }
}
