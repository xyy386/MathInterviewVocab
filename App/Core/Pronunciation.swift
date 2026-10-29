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

    var googleHost: String {
        switch self {
        case .american: "translate.google.com"
        case .british: "translate.google.co.uk"
        }
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
    case loading(EnglishAccent)
    case google(EnglishAccent)
    case systemFallback(EnglishAccent)

    var accent: EnglishAccent? {
        switch self {
        case .idle: nil
        case let .loading(accent), let .google(accent), let .systemFallback(accent):
            accent
        }
    }
}
