import Foundation
import XCTest
@testable import MathInterviewVocab

final class PronunciationTests: XCTestCase {
    func testLegacyTermWithoutPronunciationStillDecodes() throws {
        let legacyJSON = #"""
        {
          "id": 104,
          "subjectID": "analysis",
          "subjectName": "数学分析",
          "subjectNameEn": "Mathematical Analysis",
          "chinese": "无界函数",
          "english": "unbounded function",
          "definition": "函数值没有有限界。",
          "important": true,
          "speechText": "unbounded function"
        }
        """#

        let term = try JSONDecoder().decode(Term.self, from: Data(legacyJSON.utf8))

        XCTAssertEqual(term.pronunciation, .empty)
        XCTAssertEqual(term.speechText, "unbounded function")
    }

    func testPronunciationRoundTripPreservesBothAccents() throws {
        let pronunciation = TermPronunciation(
            americanIPA: "ʌnˈbaʊndɪd ˈfʌŋkʃən",
            britishIPA: "ʌnˈbaʊndɪd ˈfʌŋkʃən"
        )
        let term = Term(
            id: 104,
            subjectID: "analysis",
            subjectName: "数学分析",
            subjectNameEn: "Mathematical Analysis",
            chinese: "无界函数",
            english: "unbounded function",
            definition: "函数值没有有限界。",
            important: true,
            pronunciation: pronunciation
        )

        let data = try JSONEncoder().encode(term)
        let decoded = try JSONDecoder().decode(Term.self, from: data)

        XCTAssertEqual(decoded.pronunciation, pronunciation)
        XCTAssertEqual(
            decoded.pronunciation.ipa(for: .american),
            "ʌnˈbaʊndɪd ˈfʌŋkʃən"
        )
    }

    @MainActor
    func testBundledCorpusContainsCompleteNormalizedIPA() {
        let library = TermLibrary()

        XCTAssertNil(library.loadError)
        XCTAssertEqual(library.terms.count, 326)
        XCTAssertTrue(
            library.terms.allSatisfy {
                !$0.pronunciation.americanIPA.isEmpty
                    && !$0.pronunciation.britishIPA.isEmpty
            }
        )

        let forbiddenCharacters = CharacterSet(charactersIn: "_ᵻ\u{200D}\u{0361}")
        XCTAssertTrue(
            library.terms.allSatisfy { term in
                term.pronunciation.americanIPA.rangeOfCharacter(
                    from: forbiddenCharacters
                ) == nil
                    && term.pronunciation.britishIPA.rangeOfCharacter(
                        from: forbiddenCharacters
                    ) == nil
            }
        )

        let unbounded = library.term(id: 104)
        XCTAssertEqual(
            unbounded?.pronunciation.americanIPA,
            "ʌnˈbaʊndɪd ˈfʌŋkʃən"
        )
        XCTAssertEqual(
            unbounded?.pronunciation.britishIPA,
            "ʌnˈbaʊndɪd ˈfʌŋkʃən"
        )
    }

    @MainActor
    func testLegacyVoiceLocaleMigratesToAccent() {
        let suiteName = "SpeechServiceTests-\(UUID().uuidString)"
        let defaults = UserDefaults(suiteName: suiteName)!
        defer { defaults.removePersistentDomain(forName: suiteName) }
        defaults.set("en-GB", forKey: "voiceLocale")

        let speech = SpeechService(defaults: defaults)

        XCTAssertEqual(speech.accent, .british)
        speech.accent = .american
        speech.speed = 1.25
        XCTAssertEqual(defaults.string(forKey: "voiceLocale"), "en-US")
        XCTAssertEqual(defaults.double(forKey: "speechSpeed"), 1.25)
    }
}
