import CryptoKit
import Foundation

struct LastfmAuthCoordinator {
    private static let callbackURL = "discrobble://auth/lastfm"
    private static let handoffInfo = Data("discrobble-auth-handoff:v1".utf8)

    private let attemptStore: PendingAuthAttemptStoring
    private let decoder: JSONDecoder
    private let encoder: JSONEncoder
    private let session: URLSession
    private let workerBaseURL: URL

    init(
        attemptStore: PendingAuthAttemptStoring = KeychainPendingAuthAttemptStore(),
        session: URLSession = .shared,
        workerBaseURL: URL = AppConfiguration.workerBaseURL
    ) {
        self.attemptStore = attemptStore
        self.session = session
        self.workerBaseURL = workerBaseURL

        let decoder = JSONDecoder()
        decoder.dateDecodingStrategy = .iso8601
        self.decoder = decoder

        let encoder = JSONEncoder()
        encoder.dateEncodingStrategy = .iso8601
        self.encoder = encoder
    }

    func startAuth() async throws -> URL {
        let attempt = try attemptStore.createAttempt(for: .lastfm)
        var request = URLRequest(url: workerBaseURL.appendingPathComponent("auth/lastfm/start"))
        request.httpMethod = "POST"
        request.setValue("application/json", forHTTPHeaderField: "Content-Type")

        do {
            request.httpBody = try encoder.encode(
                LastfmAuthStartRequest(
                    callbackURL: Self.callbackURL,
                    devicePublicKey: attempt.publicKeyPEM
                )
            )

            let (data, response) = try await session.data(for: request)
            let httpResponse = try requireHTTPResponse(response)

            guard (200 ..< 300).contains(httpResponse.statusCode) else {
                throw decodeWorkerError(data)
            }

            let payload = try decoder.decode(LastfmAuthStartResponse.self, from: data)
            return payload.authorizeURL
        } catch {
            try? attemptStore.removeAttempt(for: .lastfm)
            throw error
        }
    }

    func consumeCallback(_ callback: PendingAuthCallback) throws -> StoredIntegrationTokenSet {
        guard callback.provider == .lastfm else {
            throw LastfmAuthCoordinatorError.unsupportedProvider
        }

        guard let attempt = try attemptStore.loadAttempt(for: .lastfm) else {
            throw LastfmAuthCoordinatorError.missingPendingAttempt
        }

        let envelopeData = Data(callback.encryptedPayload.utf8)
        let envelope = try decoder.decode(EncryptedAuthEnvelope.self, from: envelopeData)

        guard envelope.algorithm == "ECDH-P256+HKDF-SHA256+A256GCM" else {
            throw LastfmAuthCoordinatorError.unsupportedEnvelope
        }

        let privateKey = try P256.KeyAgreement.PrivateKey(rawRepresentation: attempt.privateKeyRawRepresentation)
        let ephemeralPublicKey = try P256.KeyAgreement.PublicKey(pemRepresentation: envelope.ephemeralPublicKeyPEM)
        let sharedSecret = try privateKey.sharedSecretFromKeyAgreement(with: ephemeralPublicKey)
        let salt = try Data(base64URLEncoded: envelope.salt)
        let iv = try Data(base64URLEncoded: envelope.iv)
        let ciphertext = try Data(base64URLEncoded: envelope.ciphertext)

        guard ciphertext.count > 16 else {
            throw LastfmAuthCoordinatorError.invalidCiphertext
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
        let payload = try decoder.decode(LastfmAuthPayload.self, from: payloadData)

        guard payload.provider == .lastfm else {
            throw LastfmAuthCoordinatorError.unsupportedProvider
        }

        guard payload.expiresAt > Date() else {
            throw LastfmAuthCoordinatorError.expiredPayload
        }

        try attemptStore.removeAttempt(for: .lastfm)
        return payload.asStoredTokenSet()
    }

    func clearAttempt(for provider: AuthProvider) throws {
        try attemptStore.removeAttempt(for: provider)
    }

    private func decodeWorkerError(_ data: Data) -> Error {
        guard let payload = try? decoder.decode(WorkerErrorEnvelope.self, from: data) else {
            return LastfmAuthCoordinatorError.unexpectedWorkerResponse
        }

        return LastfmAuthCoordinatorError.workerReturned(payload.error.message)
    }

    private func requireHTTPResponse(_ response: URLResponse) throws -> HTTPURLResponse {
        guard let httpResponse = response as? HTTPURLResponse else {
            throw LastfmAuthCoordinatorError.unexpectedWorkerResponse
        }

        return httpResponse
    }
}

private struct LastfmAuthStartRequest: Encodable {
    let callbackURL: String
    let devicePublicKey: String
    let platform = "ios"

    enum CodingKeys: String, CodingKey {
        case callbackURL = "callback_url"
        case devicePublicKey = "device_public_key"
        case platform
    }
}

private struct LastfmAuthStartResponse: Decodable {
    let authorizeURL: URL

    enum CodingKeys: String, CodingKey {
        case authorizeURL = "authorize_url"
    }
}

private struct WorkerErrorEnvelope: Decodable {
    let error: WorkerErrorPayload
}

private struct WorkerErrorPayload: Decodable {
    let code: String
    let message: String
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

private struct LastfmAuthPayload: Decodable {
    let expiresAt: Date
    let issuedAt: Date
    let provider: AuthProvider
    let sessionKey: String
    let username: String

    enum CodingKeys: String, CodingKey {
        case expiresAt = "expires_at"
        case issuedAt = "issued_at"
        case provider
        case sessionKey = "session_key"
        case username
    }

    func asStoredTokenSet() -> StoredIntegrationTokenSet {
        StoredIntegrationTokenSet(
            provider: .lastfm,
            username: username,
            accessToken: sessionKey,
            accessSecret: nil,
            issuedAt: issuedAt,
            expiresAt: nil
        )
    }
}

private enum LastfmAuthCoordinatorError: LocalizedError {
    case expiredPayload
    case invalidCiphertext
    case missingPendingAttempt
    case unexpectedWorkerResponse
    case unsupportedEnvelope
    case unsupportedProvider
    case workerReturned(String)

    var errorDescription: String? {
        switch self {
        case .expiredPayload:
            return "The Last.fm auth callback expired before the app could decrypt it. Restart the auth flow."
        case .invalidCiphertext:
            return "The Last.fm auth callback contained an invalid encrypted payload."
        case .missingPendingAttempt:
            return "No pending Last.fm auth attempt was available to decrypt the callback."
        case .unexpectedWorkerResponse:
            return "The Worker returned an unexpected Last.fm auth response."
        case .unsupportedEnvelope:
            return "The Last.fm auth callback used an unsupported encryption envelope."
        case .unsupportedProvider:
            return "Only Last.fm auth is implemented in this shell."
        case let .workerReturned(message):
            return message
        }
    }
}

private enum Base64URLError: LocalizedError {
    case invalidValue

    var errorDescription: String? {
        switch self {
        case .invalidValue:
            return "The Last.fm callback payload could not be base64url-decoded."
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
