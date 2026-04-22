import Foundation

struct AuthCallbackParser {
    func parse(url: URL) throws -> AuthCallbackResult {
        guard url.scheme?.lowercased() == "discrobble" else {
            throw AuthCallbackParserError.unsupportedScheme
        }

        let provider = try parseProvider(from: url)
        let fragment = parseFragment(from: url)

        if let errorCode = fragment["error_code"], !errorCode.isEmpty {
            let errorMessage = fragment["error_message"]?.trimmingCharacters(in: .whitespacesAndNewlines)
            return .failure(
                AuthCallbackFailure(
                    provider: provider,
                    code: errorCode,
                    message: errorMessage ?? "The provider auth flow did not complete successfully."
                )
            )
        }

        guard let payload = fragment["payload"]?.trimmingCharacters(in: .whitespacesAndNewlines), !payload.isEmpty else {
            throw AuthCallbackParserError.missingPayload
        }

        return .payload(
            PendingAuthCallback(
                provider: provider,
                encryptedPayload: payload,
                receivedAt: Date()
            )
        )
    }

    private func parseProvider(from url: URL) throws -> AuthProvider {
        guard url.host?.lowercased() == "auth" else {
            throw AuthCallbackParserError.unsupportedRoute
        }

        let route = url.path.trimmingCharacters(in: CharacterSet(charactersIn: "/")).lowercased()

        guard let provider = AuthProvider(rawValue: route) else {
            throw AuthCallbackParserError.unsupportedRoute
        }

        return provider
    }

    private func parseFragment(from url: URL) -> [String: String] {
        guard let fragment = url.fragment, !fragment.isEmpty else {
            return [:]
        }

        var fragmentComponents = URLComponents()
        fragmentComponents.scheme = "discrobble"
        fragmentComponents.host = "fragment"
        fragmentComponents.percentEncodedQuery = fragment

        return Dictionary(
            fragmentComponents
            .queryItems?
            .compactMap { item in
                guard let value = item.value else {
                    return nil
                }

                return (item.name, value)
            } ?? [],
            uniquingKeysWith: { _, last in last }
        )
    }
}

enum AuthCallbackParserError: LocalizedError {
    case unsupportedScheme
    case unsupportedRoute
    case missingPayload

    var errorDescription: String? {
        switch self {
        case .unsupportedScheme:
            return "The callback URL must use the discrobble scheme."
        case .unsupportedRoute:
            return "The callback URL must target discrobble://auth/lastfm or discrobble://auth/discogs."
        case .missingPayload:
            return "The callback URL is missing the encrypted payload fragment."
        }
    }
}
