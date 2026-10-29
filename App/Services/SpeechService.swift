@preconcurrency import AVFoundation
import Foundation
import Observation

@MainActor
@Observable
final class SpeechService {
    @ObservationIgnored private let synthesizer = AVSpeechSynthesizer()
    @ObservationIgnored private let defaults: UserDefaults
    @ObservationIgnored private var audioPlayer: AVAudioPlayer?
    @ObservationIgnored private var playbackTask: Task<Void, Never>?
    @ObservationIgnored private var audioCache: [AudioCacheKey: Data] = [:]
    @ObservationIgnored private var cacheOrder: [AudioCacheKey] = []

    private static let maximumCachedAudioCount = 80

    var accent: EnglishAccent {
        didSet {
            defaults.set(accent.rawValue, forKey: "voiceLocale")
        }
    }

    var speed: Double {
        didSet {
            defaults.set(speed, forKey: "speechSpeed")
            if let audioPlayer, audioPlayer.isPlaying {
                audioPlayer.enableRate = true
                audioPlayer.rate = Float(speed)
            }
        }
    }

    private(set) var playbackSource: SpeechPlaybackSource = .idle

    init(defaults: UserDefaults = .standard) {
        self.defaults = defaults
        let storedLocale = defaults.string(forKey: "voiceLocale") ?? "en-US"
        accent = EnglishAccent(rawValue: storedLocale) ?? .american

        let storedSpeed = defaults.double(forKey: "speechSpeed")
        speed = storedSpeed == 0 ? 1.0 : storedSpeed
    }

    func speak(_ term: Term, accent: EnglishAccent? = nil) {
        speak(text: term.speechText, accent: accent)
    }

    func speak(text: String, accent requestedAccent: EnglishAccent? = nil) {
        let normalizedText = text.trimmingCharacters(in: .whitespacesAndNewlines)
        guard !normalizedText.isEmpty else { return }

        let playbackAccent = requestedAccent ?? accent
        stopPlayback()
        playbackSource = .loading(playbackAccent)

        let cacheKey = AudioCacheKey(text: normalizedText, accent: playbackAccent)
        if let cachedAudio = audioCache[cacheKey] {
            playGoogleAudio(cachedAudio, text: normalizedText, accent: playbackAccent)
            return
        }

        let request: URLRequest
        do {
            request = try GoogleTranslatePronunciationProvider.request(
                for: normalizedText,
                accent: playbackAccent
            )
        } catch {
            playSystemFallback(text: normalizedText, accent: playbackAccent)
            return
        }

        playbackTask = Task { [weak self] in
            do {
                let (data, response) = try await URLSession.shared.data(for: request)
                let audioData = try GoogleTranslatePronunciationProvider.validatedAudio(
                    data: data,
                    response: response
                )
                try Task.checkCancellation()
                guard let self else { return }

                self.store(audioData, for: cacheKey)
                self.playGoogleAudio(
                    audioData,
                    text: normalizedText,
                    accent: playbackAccent
                )
            } catch {
                guard !Task.isCancelled, let self else { return }
                self.playSystemFallback(text: normalizedText, accent: playbackAccent)
            }
        }
    }

    func stop() {
        stopPlayback()
        playbackSource = .idle
    }

    private func stopPlayback() {
        playbackTask?.cancel()
        playbackTask = nil

        audioPlayer?.stop()
        audioPlayer = nil

        if synthesizer.isSpeaking {
            synthesizer.stopSpeaking(at: .immediate)
        }
    }

    private func playGoogleAudio(
        _ data: Data,
        text: String,
        accent: EnglishAccent
    ) {
        guard !Task.isCancelled else { return }

        do {
            let player = try AVAudioPlayer(data: data)
            player.enableRate = true
            player.rate = Float(speed)
            player.prepareToPlay()
            guard player.play() else {
                playSystemFallback(text: text, accent: accent)
                return
            }
            audioPlayer = player
            playbackTask = nil
            playbackSource = .google(accent)
        } catch {
            playSystemFallback(text: text, accent: accent)
        }
    }

    private func playSystemFallback(text: String, accent: EnglishAccent) {
        playbackTask = nil
        audioPlayer?.stop()
        audioPlayer = nil

        let utterance = AVSpeechUtterance(string: text)
        utterance.voice = preferredSystemVoice(for: accent)

        let requestedRate = AVSpeechUtteranceDefaultSpeechRate * Float(speed)
        utterance.rate = min(
            max(requestedRate, AVSpeechUtteranceMinimumSpeechRate),
            AVSpeechUtteranceMaximumSpeechRate
        )
        utterance.pitchMultiplier = 1.0
        utterance.preUtteranceDelay = 0.04
        playbackSource = .systemFallback(accent)
        synthesizer.speak(utterance)
    }

    private func preferredSystemVoice(for accent: EnglishAccent) -> AVSpeechSynthesisVoice? {
        let exactVoices = AVSpeechSynthesisVoice.speechVoices().filter {
            $0.language.caseInsensitiveCompare(accent.rawValue) == .orderedSame
        }
        return exactVoices.max { lhs, rhs in
            lhs.quality.rawValue < rhs.quality.rawValue
        } ?? AVSpeechSynthesisVoice(language: accent.rawValue)
            ?? AVSpeechSynthesisVoice.speechVoices().first {
                $0.language.hasPrefix("en")
            }
    }

    private func store(_ data: Data, for key: AudioCacheKey) {
        if audioCache[key] == nil {
            cacheOrder.append(key)
        }
        audioCache[key] = data

        while cacheOrder.count > Self.maximumCachedAudioCount {
            let oldestKey = cacheOrder.removeFirst()
            audioCache.removeValue(forKey: oldestKey)
        }
    }
}

private struct AudioCacheKey: Hashable {
    let text: String
    let accent: EnglishAccent
}
