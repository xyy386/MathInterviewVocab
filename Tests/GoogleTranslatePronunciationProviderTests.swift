import Foundation
import XCTest
@testable import MathInterviewVocab

final class GoogleTranslatePronunciationProviderTests: XCTestCase {
    func testAmericanAndBritishRequestsUseMatchingHostsAndLocales() throws {
        let american = try GoogleTranslatePronunciationProvider.request(
            for: "unbounded function",
            accent: .american
        )
        let british = try GoogleTranslatePronunciationProvider.request(
            for: "unbounded function",
            accent: .british
        )

        XCTAssertEqual(american.url?.host, "translate.google.com")
        XCTAssertEqual(british.url?.host, "translate.google.co.uk")
        XCTAssertEqual(queryValue(named: "tl", in: american), "en-US")
        XCTAssertEqual(queryValue(named: "tl", in: british), "en-GB")
        XCTAssertEqual(queryValue(named: "q", in: american), "unbounded function")
    }

    func testRequestPreservesUnicodeApostrophesAndHyphens() throws {
        let request = try GoogleTranslatePronunciationProvider.request(
            for: "  Green’s semi-bounded function  ",
            accent: .british
        )

        XCTAssertEqual(
            queryValue(named: "q", in: request),
            "Green’s semi-bounded function"
        )
    }

    func testRequestRejectsEmptyAndOverlongText() {
        XCTAssertThrowsError(
            try GoogleTranslatePronunciationProvider.request(
                for: "   ",
                accent: .american
            )
        )
        XCTAssertThrowsError(
            try GoogleTranslatePronunciationProvider.request(
                for: String(repeating: "a", count: 501),
                accent: .american
            )
        )
    }

    func testValidatedAudioAcceptsSuccessfulMPEGResponse() throws {
        let data = Data(repeating: 0x42, count: 256)
        let response = makeResponse(statusCode: 200, contentType: "audio/mpeg")

        XCTAssertEqual(
            try GoogleTranslatePronunciationProvider.validatedAudio(
                data: data,
                response: response
            ),
            data
        )
    }

    func testValidatedAudioRejectsHTTPErrorHTMLAndEmptyPayload() {
        XCTAssertThrowsError(
            try GoogleTranslatePronunciationProvider.validatedAudio(
                data: Data(repeating: 0x42, count: 256),
                response: makeResponse(statusCode: 429, contentType: "audio/mpeg")
            )
        )
        XCTAssertThrowsError(
            try GoogleTranslatePronunciationProvider.validatedAudio(
                data: Data(repeating: 0x42, count: 256),
                response: makeResponse(statusCode: 200, contentType: "text/html")
            )
        )
        XCTAssertThrowsError(
            try GoogleTranslatePronunciationProvider.validatedAudio(
                data: Data(),
                response: makeResponse(statusCode: 200, contentType: "audio/mpeg")
            )
        )
    }

    private func queryValue(named name: String, in request: URLRequest) -> String? {
        guard let url = request.url else { return nil }
        return URLComponents(url: url, resolvingAgainstBaseURL: false)?
            .queryItems?
            .first { $0.name == name }?
            .value
    }

    private func makeResponse(
        statusCode: Int,
        contentType: String
    ) -> HTTPURLResponse {
        HTTPURLResponse(
            url: URL(string: "https://translate.google.com/translate_tts")!,
            statusCode: statusCode,
            httpVersion: "HTTP/1.1",
            headerFields: ["Content-Type": contentType]
        )!
    }
}
