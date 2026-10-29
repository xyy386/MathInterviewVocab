import CryptoKit
import Foundation

enum BundledPronunciationError: Error, Equatable {
    case invalidText
    case invalidManifest
    case missingAudio(String)
    case invalidAudio
}

/// Reads the same verified, pre-generated Kokoro clips used by Windows.
struct BundledPronunciationProvider: Sendable {
    struct Clip: Decodable, Sendable {
        let text: String
        let accent: EnglishAccent
        let file: String
        let sha256: String
        let bytes: Int
    }

    struct Manifest: Decodable, Sendable {
        struct Coverage: Decodable, Sendable {
            let expectedEntries: Int
            let complete: Bool
        }

        let version: Int
        let source: String
        let entries: [String: Clip]
        let coverage: Coverage
    }

    let manifest: Manifest
    private let directoryURL: URL

    init(bundle: Bundle = .main) throws {
        guard let resources = bundle.resourceURL else {
            throw BundledPronunciationError.invalidManifest
        }
        try self.init(directoryURL: resources.appendingPathComponent("KokoroAudio", isDirectory: true))
    }

    init(directoryURL: URL) throws {
        guard directoryURL.isFileURL else {
            throw BundledPronunciationError.invalidManifest
        }
        let manifest: Manifest
        do {
            let data = try Data(contentsOf: directoryURL.appendingPathComponent("audio-manifest.json"))
            manifest = try JSONDecoder().decode(Manifest.self, from: data)
        } catch {
            throw BundledPronunciationError.invalidManifest
        }
        guard manifest.version == 1,
              manifest.source.hasPrefix("Kokoro-82M"),
              manifest.coverage.complete,
              !manifest.entries.isEmpty,
              manifest.entries.count == manifest.coverage.expectedEntries else {
            throw BundledPronunciationError.invalidManifest
        }
        for (key, clip) in manifest.entries {
            guard Self.isDigest(key), Self.isDigest(clip.sha256),
                  clip.file == "\(key).mp3", clip.bytes >= 128,
                  key == Self.key(for: clip.text, accent: clip.accent),
                  Self.normalize(clip.text) == clip.text else {
                throw BundledPronunciationError.invalidManifest
            }
        }
        self.manifest = manifest
        self.directoryURL = directoryURL
    }

    // 与 Windows 保持相同的 NFKC、标点和空白规则，不把缩写转成小写。
    static func normalize(_ text: String) -> String {
        text.precomposedStringWithCompatibilityMapping
            .replacingOccurrences(of: "[‘’ʼ＇]", with: "'", options: .regularExpression)
            .replacingOccurrences(of: "[‐‑‒–—−]", with: "-", options: .regularExpression)
            .replacingOccurrences(of: "\\s+", with: " ", options: .regularExpression)
            .trimmingCharacters(in: .whitespacesAndNewlines)
    }

    static func key(for text: String, accent: EnglishAccent) -> String {
        digest(Data("\(accent.rawValue)\n\(text)".utf8))
    }

    func audioData(for input: String, accent: EnglishAccent) throws -> Data {
        guard input.utf16.count <= 600,
              let selection = SpeechSelectionSanitizer.sanitize(input) else {
            throw BundledPronunciationError.invalidText
        }
        let text = Self.normalize(selection)
        let key = Self.key(for: text, accent: accent)
        guard let clip = manifest.entries[key], clip.accent == accent, clip.text == text else {
            throw BundledPronunciationError.missingAudio(text)
        }

        // 文件名只来自已验证的清单，选区不能变成文件路径或远程 URL。
        let fileURL = directoryURL.appendingPathComponent("audio", isDirectory: true)
            .appendingPathComponent(clip.file)
        let data: Data
        do {
            data = try Data(contentsOf: fileURL)
        } catch {
            throw BundledPronunciationError.invalidAudio
        }
        guard data.count == clip.bytes, Self.digest(data) == clip.sha256 else {
            throw BundledPronunciationError.invalidAudio
        }
        return data
    }

    private static func digest(_ data: Data) -> String {
        SHA256.hash(data: data).map { String(format: "%02x", $0) }.joined()
    }

    private static func isDigest(_ text: String) -> Bool {
        text.utf8.count == 64 && text.utf8.allSatisfy {
            (48 ... 57).contains($0) || (97 ... 102).contains($0)
        }
    }
}
