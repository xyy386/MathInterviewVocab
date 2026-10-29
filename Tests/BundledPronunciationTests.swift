import AVFoundation
import CryptoKit
import Foundation
import Testing
@testable import MathInterviewVocab

struct BundledPronunciationTests {
    @Test func bundledManifestContainsCompleteKokoroInventory() throws {
        let provider = try BundledPronunciationProvider()
        let manifest = provider.manifest

        #expect(manifest.version == 1)
        #expect(manifest.source.contains("Kokoro-82M"))
        #expect(manifest.source.contains("af_heart en-US"))
        #expect(manifest.source.contains("bf_emma en-GB"))
        #expect(!manifest.source.localizedCaseInsensitiveContains("Google"))
        #expect(manifest.coverage.complete)
        #expect(manifest.entries.count == 2328)
        #expect(manifest.coverage.expectedEntries == 2328)

        let american = Set(manifest.entries.values.filter { $0.accent == .american }.map(\.text))
        let british = Set(manifest.entries.values.filter { $0.accent == .british }.map(\.text))
        #expect(american.count == 1164)
        #expect(american == british)
    }

    @Test func everyBundledClipPassesByteHashAndMPEGValidation() throws {
        let provider = try BundledPronunciationProvider()
        for (key, clip) in provider.manifest.entries {
            let data = try provider.audioData(for: clip.text, accent: clip.accent)
            #expect(key == BundledPronunciationProvider.key(for: clip.text, accent: clip.accent))
            #expect(clip.file == "\(key).mp3")
            #expect(data.count == clip.bytes)
            #expect(Self.digest(data) == clip.sha256)
            try #require(data.count >= 128)
            let hasID3Header = data.prefix(3) == Data("ID3".utf8)
            let hasMPEGHeader = data[0] == 0xff && data[1] & 0xe0 == 0xe0
            #expect(hasID3Header || hasMPEGHeader)
        }
    }

    @Test @MainActor func everyTermAndCompleteLatinWordHasBothAccents() throws {
        let library = TermLibrary()
        let provider = try BundledPronunciationProvider()
        #expect(library.loadError == nil)
        #expect(library.terms.count == 326)
        #expect(library.terms.filter(\.important).count == 130)
        let wordPattern = try NSRegularExpression(
            pattern: #"[\p{script=Latin}\p{N}]+(?:['-][\p{script=Latin}\p{N}]+)*"#
        )

        for term in library.terms {
            for accent in EnglishAccent.allCases {
                #expect(try !provider.audioData(for: term.speechText, accent: accent).isEmpty)
            }
            let english = BundledPronunciationProvider.normalize(MathNotation.display(term.english))
            let matches = wordPattern.matches(in: english, range: NSRange(english.startIndex..., in: english))
            for match in matches {
                let range = try #require(Range(match.range, in: english))
                let word = String(english[range])
                for accent in EnglishAccent.allCases {
                    #expect(try !provider.audioData(for: word, accent: accent).isEmpty)
                }
            }
        }
    }

    @Test(arguments: ["unbounded function", "Mathematical Analysis", "Probability Theory"])
    func accentsAreDistinctAudioNotRelabeledCopies(text: String) throws {
        let provider = try BundledPronunciationProvider()
        let american = try provider.audioData(for: text, accent: .american)
        let british = try provider.audioData(for: text, accent: .british)
        #expect(Self.digest(american) != Self.digest(british))
    }

    @Test(arguments: [EnglishAccent.american, .british])
    func nativeDecoderReadsWordAndPhraseWithoutPlayback(accent: EnglishAccent) throws {
        let provider = try BundledPronunciationProvider()
        let directory = FileManager.default.temporaryDirectory
            .appendingPathComponent("KokoroDecodeTests-\(UUID().uuidString)", isDirectory: true)
        try FileManager.default.createDirectory(at: directory, withIntermediateDirectories: true)
        defer { try? FileManager.default.removeItem(at: directory) }

        for text in ["unbounded", "unbounded function", "Mathematical Analysis"] {
            let data = try provider.audioData(for: text, accent: accent)
            let fileURL = directory.appendingPathComponent("sample.mp3")
            try data.write(to: fileURL)
            let audioFile = try AVAudioFile(forReading: fileURL)
            #expect(audioFile.length > 0)
            #expect(audioFile.processingFormat.sampleRate > 0)
            #expect(audioFile.processingFormat.channelCount > 0)
            let player = try AVAudioPlayer(data: data)
            player.enableRate = true
            for speed in [0.75, 1.0, 1.25] {
                player.rate = Float(speed)
                #expect(player.rate == Float(speed))
            }
            #expect(!player.isPlaying)
        }
    }

    @Test func normalizationMatchesWindowsAndPreservesAcronymCase() throws {
        #expect(BundledPronunciationProvider.normalize("  ＭＬＥ\t  Dini’s\u{00a0}theorem — semi‑bounded ") == "MLE Dini's theorem - semi-bounded")
        #expect(BundledPronunciationProvider.normalize("L²\n space") == "L2 space")
        #expect(BundledPronunciationProvider.normalize("ʌnˈbaʊndɪd") == "ʌnˈbaʊndɪd")
        #expect(BundledPronunciationProvider.key(for: "MLE", accent: .american) != BundledPronunciationProvider.key(for: "mle", accent: .american))

        let provider = try BundledPronunciationProvider()
        for accent in EnglishAccent.allCases {
            let expected = try provider.audioData(for: "unbounded", accent: accent)
            #expect(try provider.audioData(for: "“unbounded,”", accent: accent) == expected)
            #expect(try provider.audioData(for: "unbounded\n\tfunction", accent: accent) == provider.audioData(for: "unbounded function", accent: accent))
            #expect(try provider.audioData(for: "Ｇｒｅｅｎ’s theorem", accent: accent) == provider.audioData(for: "Green's theorem", accent: accent))
        }
    }

    @Test func unsupportedSelectionsCannotBecomePathsOrNetworkRequests() throws {
        let provider = try BundledPronunciationProvider()
        for input in ["unbounde", "zzzz-unrecorded-selection", "../progress.json", "https://example.invalid/test.mp3"] {
            let selection = try #require(SpeechSelectionSanitizer.sanitize(input))
            let normalized = BundledPronunciationProvider.normalize(selection)
            #expect(throws: BundledPronunciationError.missingAudio(normalized)) {
                try provider.audioData(for: input, accent: .american)
            }
        }
        for input in ["", "  , — …  ", String(repeating: "x", count: 601)] {
            #expect(throws: BundledPronunciationError.invalidText) {
                try provider.audioData(for: input, accent: .british)
            }
        }
        let remoteURL = try #require(URL(string: "https://example.invalid/audio"))
        #expect(throws: BundledPronunciationError.invalidManifest) {
            try BundledPronunciationProvider(directoryURL: remoteURL)
        }
    }

    @Test(arguments: ["malformed", "traversal", "coverage", "key"])
    func corruptManifestFailsClosed(corruption: String) throws {
        try Self.withAudioFixture { directory, key, _ in
            let fileURL = directory.appendingPathComponent("audio-manifest.json")
            if corruption == "malformed" {
                try Data("not JSON".utf8).write(to: fileURL)
            } else {
                var manifest = try #require(JSONSerialization.jsonObject(with: Data(contentsOf: fileURL)) as? [String: Any])
                if corruption == "coverage" {
                    manifest["coverage"] = ["expectedEntries": 1, "complete": false]
                } else {
                    var entries = try #require(manifest["entries"] as? [String: [String: Any]])
                    var entry = try #require(entries[key])
                    if corruption == "traversal" { entry["file"] = "../progress.json" }
                    if corruption == "key" { entry["text"] = "changed text" }
                    entries[key] = entry
                    manifest["entries"] = entries
                }
                try JSONSerialization.data(withJSONObject: manifest).write(to: fileURL)
            }
            #expect(throws: BundledPronunciationError.invalidManifest) {
                try BundledPronunciationProvider(directoryURL: directory)
            }
        }
    }

    @Test(arguments: ["missing", "truncated", "tampered"])
    func damagedAudioIsNeverPlayed(corruption: String) throws {
        try Self.withAudioFixture { directory, key, originalAudio in
            let provider = try BundledPronunciationProvider(directoryURL: directory)
            let fileURL = directory.appendingPathComponent("audio/\(key).mp3")
            switch corruption {
            case "missing":
                try FileManager.default.removeItem(at: fileURL)
            case "truncated":
                try originalAudio.dropLast().write(to: fileURL)
            default:
                var tampered = originalAudio
                tampered[tampered.startIndex] ^= 0xff
                try tampered.write(to: fileURL)
            }
            #expect(throws: BundledPronunciationError.invalidAudio) {
                try provider.audioData(for: "unbounded", accent: .american)
            }
        }
    }

    @Test @MainActor func speechServiceReportsMissingOrBrokenResourcesWithoutFallback() throws {
        let suiteName = "KokoroSpeechTests-\(UUID().uuidString)"
        let defaults = try #require(UserDefaults(suiteName: suiteName))
        defer { defaults.removePersistentDomain(forName: suiteName) }
        defaults.set("en-GB", forKey: "voiceLocale")
        defaults.set(7.0, forKey: "speechSpeed")
        let speech = SpeechService(defaults: defaults)

        #expect(speech.accent == .british)
        #expect(speech.speed == 1.0)
        speech.speak(text: "unbounde")
        #expect(speech.playbackSource == .missing(.british, text: "unbounde"))
        speech.speak(text: "  , — …  ")
        #expect(speech.playbackSource == .idle)
        speech.stop()
        #expect(speech.playbackSource == .idle)

        let missingDirectory = FileManager.default.temporaryDirectory
            .appendingPathComponent("NoKokoroResources-\(UUID().uuidString)", isDirectory: true)
        let broken = SpeechService(defaults: defaults, audioDirectoryURL: missingDirectory)
        broken.speak(text: "unbounded", accent: .american)
        #expect(broken.playbackSource == .failed(.american))
        broken.stop()
        #expect(broken.playbackSource == .idle)
    }

    private static func digest(_ data: Data) -> String {
        SHA256.hash(data: data).map { String(format: "%02x", $0) }.joined()
    }

    private static func withAudioFixture(_ body: (URL, String, Data) throws -> Void) throws {
        let provider = try BundledPronunciationProvider()
        let key = BundledPronunciationProvider.key(for: "unbounded", accent: .american)
        let clip = try #require(provider.manifest.entries[key])
        let audio = try provider.audioData(for: clip.text, accent: clip.accent)
        let directory = FileManager.default.temporaryDirectory
            .appendingPathComponent("KokoroResourceTests-\(UUID().uuidString)", isDirectory: true)
        try FileManager.default.createDirectory(at: directory.appendingPathComponent("audio"), withIntermediateDirectories: true)
        defer { try? FileManager.default.removeItem(at: directory) }
        let manifest: [String: Any] = [
            "version": 1, "source": provider.manifest.source,
            "coverage": ["expectedEntries": 1, "complete": true],
            "entries": [key: ["text": clip.text, "accent": clip.accent.rawValue, "file": clip.file, "sha256": clip.sha256, "bytes": clip.bytes]]
        ]
        try JSONSerialization.data(withJSONObject: manifest).write(to: directory.appendingPathComponent("audio-manifest.json"))
        try audio.write(to: directory.appendingPathComponent("audio/\(clip.file)"))
        try body(directory, key, audio)
    }
}
