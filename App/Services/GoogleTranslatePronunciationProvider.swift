import Foundation

enum GoogleTranslatePronunciationError: Error, Equatable {
    case invalidText
    case invalidResponse
    case httpStatus(Int)
    case unexpectedContentType(String?)
    case emptyAudio
}

struct GoogleTranslatePronunciationProvider: Sendable {
    static let maximumUTF8Length = 500

    static func request(
        for text: String,
        accent: EnglishAccent
    ) throws -> URLRequest {
        let normalizedText = text.trimmingCharacters(in: .whitespacesAndNewlines)
        guard
            !normalizedText.isEmpty,
            normalizedText.lengthOfBytes(using: .utf8) <= maximumUTF8Length
        else {
            throw GoogleTranslatePronunciationError.invalidText
        }

        var components = URLComponents()
        components.scheme = "https"
        components.host = accent.googleHost
        components.path = "/translate_tts"
        components.queryItems = [
            URLQueryItem(name: "ie", value: "UTF-8"),
            URLQueryItem(name: "client", value: "tw-ob"),
            URLQueryItem(name: "tl", value: accent.rawValue),
            URLQueryItem(name: "q", value: normalizedText),
        ]

        guard let url = components.url else {
            throw GoogleTranslatePronunciationError.invalidText
        }

        var request = URLRequest(url: url)
        request.timeoutInterval = 8
        request.cachePolicy = .reloadRevalidatingCacheData
        request.setValue("audio/mpeg", forHTTPHeaderField: "Accept")
        request.setValue(accent.rawValue, forHTTPHeaderField: "Accept-Language")
        request.setValue(
            "Mozilla/5.0 (Macintosh; Intel Mac OS X 14_0) AppleWebKit/605.1.15",
            forHTTPHeaderField: "User-Agent"
        )
        return request
    }

    static func validatedAudio(
        data: Data,
        response: URLResponse
    ) throws -> Data {
        guard let httpResponse = response as? HTTPURLResponse else {
            throw GoogleTranslatePronunciationError.invalidResponse
        }
        guard (200 ... 299).contains(httpResponse.statusCode) else {
            throw GoogleTranslatePronunciationError.httpStatus(httpResponse.statusCode)
        }

        let mimeType = httpResponse.mimeType?.lowercased()
        guard mimeType?.hasPrefix("audio/") == true else {
            throw GoogleTranslatePronunciationError.unexpectedContentType(mimeType)
        }
        guard data.count >= 128 else {
            throw GoogleTranslatePronunciationError.emptyAudio
        }
        return data
    }
}
