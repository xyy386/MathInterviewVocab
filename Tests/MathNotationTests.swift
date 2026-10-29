import XCTest
@testable import MathInterviewVocab

final class MathNotationTests: XCTestCase {
    func testMembershipAndTransposeRenderAsUnicode() {
        XCTAssertEqual(
            MathNotation.display("\\xi\\in(a,b)"),
            "ξ∈(a,b)"
        )
        XCTAssertEqual(
            MathNotation.display("Q^\\mathsf{T}Q"),
            "QᵀQ"
        )
    }
}
