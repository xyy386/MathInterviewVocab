import XCTest
@testable import MathInterviewVocab

final class SpeechSelectionSanitizerTests: XCTestCase {
    func testExtractsSingleWordUsingUTF16Range() {
        let source = "∫ unbounded function"
        let range = (source as NSString).range(of: "unbounded")

        XCTAssertEqual(
            SpeechSelectionSanitizer.selectedText(in: source, range: range),
            "unbounded"
        )
    }

    func testPreservesMultipleWords() {
        XCTAssertEqual(
            SpeechSelectionSanitizer.sanitize("  unbounded function  "),
            "unbounded function"
        )
    }

    func testPreservesUnicodeLetters() {
        let source = "emoji 😀 naïve function"
        let range = (source as NSString).range(of: "naïve")

        XCTAssertEqual(
            SpeechSelectionSanitizer.selectedText(in: source, range: range),
            "naïve"
        )
    }

    func testPreservesInternalHyphensAndApostrophes() {
        XCTAssertEqual(
            SpeechSelectionSanitizer.sanitize("semi-bounded"),
            "semi-bounded"
        )
        XCTAssertEqual(
            SpeechSelectionSanitizer.sanitize("Dini's theorem"),
            "Dini's theorem"
        )
    }

    func testTrimsSurroundingPunctuation() {
        XCTAssertEqual(
            SpeechSelectionSanitizer.sanitize("“unbounded,”"),
            "unbounded"
        )
    }

    func testRejectsWhitespacePunctuationAndInvalidRanges() {
        XCTAssertNil(SpeechSelectionSanitizer.sanitize("  , — …  "))
        XCTAssertNil(
            SpeechSelectionSanitizer.selectedText(
                in: "bounded",
                range: NSRange(location: 99, length: 1)
            )
        )
        XCTAssertNil(
            SpeechSelectionSanitizer.selectedText(
                in: "bounded",
                range: NSRange(location: 0, length: 0)
            )
        )
    }
}
