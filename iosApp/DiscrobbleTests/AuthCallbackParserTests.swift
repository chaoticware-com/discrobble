import XCTest
@testable import Discrobble

final class AuthCallbackParserTests: XCTestCase {
    private let parser = AuthCallbackParser()

    func testParsePayloadCallbackReturnsProviderAndPayload() throws {
        let callback = try payloadCallback(
            from: parser.parse(
                url: try XCTUnwrap(URL(string: "discrobble://auth/lastfm#payload=encrypted-token")),
            ),
        )

        XCTAssertEqual(callback.provider, .lastfm)
        XCTAssertEqual(callback.encryptedPayload, "encrypted-token")
    }

    func testParseFailureCallbackReturnsProviderError() throws {
        let result = try parser.parse(
            url: try XCTUnwrap(
                URL(
                    string: "discrobble://auth/discogs#error_code=access_denied&error_message=User%20cancelled",
                ),
            ),
        )

        guard case let .failure(failure) = result else {
            XCTFail("Expected a failure callback result.")
            return
        }

        XCTAssertEqual(
            failure,
            AuthCallbackFailure(
                provider: .discogs,
                code: "access_denied",
                message: "User cancelled",
            ),
        )
    }

    func testParseFragmentKeepsLastDuplicatePayloadValue() throws {
        let callback = try payloadCallback(
            from: parser.parse(
                url: try XCTUnwrap(URL(string: "discrobble://auth/lastfm#payload=stale&payload=fresh")),
            ),
        )

        XCTAssertEqual(callback.encryptedPayload, "fresh")
    }

    private func payloadCallback(from result: AuthCallbackResult) throws -> PendingAuthCallback {
        guard case let .payload(callback) = result else {
            XCTFail("Expected a payload callback result.")
            throw ParserTestError.expectedPayload
        }

        return callback
    }
}

private enum ParserTestError: Error {
    case expectedPayload
}
