@preconcurrency import AVFoundation
import Foundation
import Observation

@MainActor
@Observable
final class SpeechService {
    @ObservationIgnored private let defaults: UserDefaults
    @ObservationIgnored private let audioProvider: Result<BundledPronunciationProvider, Error>
    @ObservationIgnored private var audioPlayer: AVAudioPlayer?

    var accent: EnglishAccent {
        didSet {
            defaults.set(accent.rawValue, forKey: "voiceLocale")
        }
    }

    var speed: Double {
        didSet {
            defaults.set(speed, forKey: "speechSpeed")
            if let audioPlayer, audioPlayer.isPlaying {
                audioPlayer.rate = Float(speed)
            }
        }
    }

    private(set) var playbackSource: SpeechPlaybackSource = .idle

    init(defaults: UserDefaults = .standard, audioDirectoryURL: URL? = nil) {
        self.defaults = defaults
        let storedLocale = defaults.string(forKey: "voiceLocale") ?? "en-US"
        accent = EnglishAccent(rawValue: storedLocale) ?? .american

        let storedSpeed = defaults.double(forKey: "speechSpeed")
        speed = [0.75, 1.0, 1.25].contains(storedSpeed) ? storedSpeed : 1.0
        audioProvider = Result {
            if let audioDirectoryURL {
                try BundledPronunciationProvider(directoryURL: audioDirectoryURL)
            } else {
                try BundledPronunciationProvider()
            }
        }
    }

    func speak(_ term: Term, accent: EnglishAccent? = nil) {
        speak(text: term.speechText, accent: accent)
    }

    func speak(text: String, accent requestedAccent: EnglishAccent? = nil) {
        stop()
        guard let selection = SpeechSelectionSanitizer.sanitize(text) else { return }
        let playbackAccent = requestedAccent ?? accent

        do {
            let audio = try audioProvider.get().audioData(for: selection, accent: playbackAccent)
            let player = try AVAudioPlayer(data: audio)
            player.enableRate = true
            player.rate = Float(speed)
            guard player.prepareToPlay(), player.play() else {
                playbackSource = .failed(playbackAccent)
                return
            }
            audioPlayer = player
            playbackSource = .kokoro(playbackAccent)
        } catch let BundledPronunciationError.missingAudio(missingText) {
            playbackSource = .missing(playbackAccent, text: missingText)
        } catch {
            playbackSource = .failed(playbackAccent)
        }
    }

    func stop() {
        audioPlayer?.stop()
        audioPlayer = nil
        playbackSource = .idle
    }
}
