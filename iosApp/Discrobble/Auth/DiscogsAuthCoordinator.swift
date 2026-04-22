import CryptoKit
import Foundation

struct DiscogsAuthCoordinator {
    private static let callbackURL = "discrobble://auth/discogs"
    private static let handoffInfo = Data("discrobble-auth-handoff:v1".utf8)

    private let attemptStore: PendingAuthAttemptStoring
    private let decoder: JSONDecoder
    private let workerBaseURL: URL

    init(
        attemptStore: PendingAuthAttemptStoring = KeychainPendingAuthAttemptStore(),
        workerBaseURL: URL = AppConfiguration.workerBaseURL
    ) {
        self.attemptStore = attemptStore
        self.workerBaseURL = workerBaseURL

        let decoder = JSONDecoder()
        decoder.dateDecodingStrategy = .iso8601
        self.decoder = decoder
    }

    func startAuth() throws -> URL {
        let attempt = try attemptStore.createAttempt(for: .discogs)

        var components = URLComponents(
            url: workerBaseURL.appendingPathComponent("auth/discogs/start"),
            resolvingAgainstBaseURL: false
        )
        components?.queryItems = [
            URLQueryItem(name: "callback_url", value: Self.callbackURL),
            URLQueryItem(name: "device_public_key", value: attempt.publicKeyPEM),
            URLQueryItem(name: "platform", value: "ios"),
        ]

        guard let authorizeURL = components?.url else {
            try? attemptStore.removeAttempt(for: .discogs)
            throw DiscogsAuthCoordinatorError.invalidAuthorizeURL
        }

        return authorizeURL
    }

    func consumeCallback(_ callback: PendingAuthCallback) throws -> StoredIntegrationTokenSet {
        guard callback.provider == .discogs else {
            throw DiscogsAuthCoordinatorError.unsupportedProvider
        }

        guard let attempt = try attemptStore.loadAttempt(for: .discogs) else {
            throw DiscogsAuthCoordinatorError.missingPendingAttempt
        }
        defer {
            try? attemptStore.removeAttempt(for: .discogs)
        }

        let envelopeData = Data(callback.encryptedPayload.utf8)
        let envelope = try decoder.decode(EncryptedAuthEnvelope.self, from: envelopeData)

        guard envelope.algorithm == "ECDH-P256+HKDF-SHA256+A256GCM" else {
            throw DiscogsAuthCoordinatorError.unsupportedEnvelope
        }

        let privateKey = try P256.KeyAgreement.PrivateKey(rawRepresentation: attempt.privateKeyRawRepresentation)
        let ephemeralPublicKey = try P256.KeyAgreement.PublicKey(pemRepresentation: envelope.ephemeralPublicKeyPEM)
        let sharedSecret = try privateKey.sharedSecretFromKeyAgreement(with: ephemeralPublicKey)
        let salt = try Data(base64URLEncoded: envelope.salt)
        let iv = try Data(base64URLEncoded: envelope.iv)
        let ciphertext = try Data(base64URLEncoded: envelope.ciphertext)

        guard ciphertext.count > 16 else {
            throw DiscogsAuthCoordinatorError.invalidCiphertext
        }

        let symmetricKey = sharedSecret.hkdfDerivedSymmetricKey(
            using: SHA256.self,
            salt: salt,
            sharedInfo: Self.handoffInfo,
            outputByteCount: 32
        )
        let sealedBox = try AES.GCM.SealedBox(
            nonce: AES.GCM.Nonce(data: iv),
            ciphertext: ciphertext.dropLast(16),
            tag: ciphertext.suffix(16)
        )
        let payloadData = try AES.GCM.open(sealedBox, using: symmetricKey)
        let payload = try decoder.decode(DiscogsAuthPayload.self, from: payloadData)

        guard payload.provider == .discogs else {
            throw DiscogsAuthCoordinatorError.unsupportedProvider
        }

        guard payload.expiresAt > Date() else {
            throw DiscogsAuthCoordinatorError.expiredPayload
        }

        return payload.asStoredTokenSet()
    }

    func clearAttempt(for provider: AuthProvider) throws {
        try attemptStore.removeAttempt(for: provider)
    }
}

private struct EncryptedAuthEnvelope: Decodable {
    let algorithm: String
    let ciphertext: String
    let ephemeralPublicKeyPEM: String
    let iv: String
    let salt: String

    enum CodingKeys: String, CodingKey {
        case algorithm = "alg"
        case ciphertext
        case ephemeralPublicKeyPEM = "epk"
        case iv
        case salt
    }
}

private struct DiscogsAuthPayload: Decodable {
    let expiresAt: Date
    let issuedAt: Date
    let oauthToken: String
    let oauthTokenSecret: String
    let provider: AuthProvider
    let username: String

    enum CodingKeys: String, CodingKey {
        case expiresAt = "expires_at"
        case issuedAt = "issued_at"
        case oauthToken = "oauth_token"
        case oauthTokenSecret = "oauth_token_secret"
        case provider
        case username
    }

    func asStoredTokenSet() -> StoredIntegrationTokenSet {
        StoredIntegrationTokenSet(
            provider: .discogs,
            username: username,
            accessToken: oauthToken,
            accessSecret: oauthTokenSecret,
            issuedAt: issuedAt,
            expiresAt: nil
        )
    }
}

private enum DiscogsAuthCoordinatorError: LocalizedError {
    case expiredPayload
    case invalidAuthorizeURL
    case invalidCiphertext
    case missingPendingAttempt
    case unsupportedEnvelope
    case unsupportedProvider

    var errorDescription: String? {
        switch self {
        case .expiredPayload:
            return "The Discogs auth callback expired before the app could decrypt it. Restart the auth flow."
        case .invalidAuthorizeURL:
            return "The Worker base URL could not be converted into a Discogs auth start URL."
        case .invalidCiphertext:
            return "The Discogs auth callback contained an invalid encrypted payload."
        case .missingPendingAttempt:
            return "No pending Discogs auth attempt was available to decrypt the callback."
        case .unsupportedEnvelope:
            return "The Discogs auth callback used an unsupported encryption envelope."
        case .unsupportedProvider:
            return "Only Discogs auth callbacks can be decrypted by this coordinator."
        }
    }
}

private enum Base64URLError: LocalizedError {
    case invalidValue

    var errorDescription: String? {
        switch self {
        case .invalidValue:
            return "The Discogs callback payload could not be base64url-decoded."
        }
    }
}

private extension Data {
    init(base64URLEncoded value: String) throws {
        let base64 = value
            .replacingOccurrences(of: "-", with: "+")
            .replacingOccurrences(of: "_", with: "/")
        let padded = base64.padding(
            toLength: ((base64.count + 3) / 4) * 4,
            withPad: "=",
            startingAt: 0
        )

        guard let data = Data(base64Encoded: padded) else {
            throw Base64URLError.invalidValue
        }

        self = data
    }
}
