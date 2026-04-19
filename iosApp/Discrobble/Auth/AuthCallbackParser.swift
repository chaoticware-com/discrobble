import Foundation

struct AuthCallbackParser {
    func parse(url: URL) throws -> PendingAuthCallback {
        guard url.scheme?.lowercased() == "discrobble" else {
            throw AuthCallbackParserError.unsupportedScheme
        }

        let provider = try parseProvider(from: url)
        let payload = try parsePayload(from: url)

        return PendingAuthCallback(
            provider: provider,
            encryptedPayload: payload,
            receivedAt: Date()
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

    private func parsePayload(from url: URL) throws -> String {
        guard let fragment = url.fragment, !fragment.isEmpty else {
            throw AuthCallbackParserError.missingPayload
        }

        var fragmentComponents = URLComponents()
        fragmentComponents.scheme = "discrobble"
        fragmentComponents.host = "fragment"
        fragmentComponents.query = fragment

        let payload = fragmentComponents
            .queryItems?
            .first(where: { $0.name == "payload" })?
            .value?
            .trimmingCharacters(in: .whitespacesAndNewlines)

        guard let payload, !payload.isEmpty else {
            throw AuthCallbackParserError.missingPayload
        }

        return payload
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
