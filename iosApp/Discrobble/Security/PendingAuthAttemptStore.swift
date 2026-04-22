import CryptoKit
import Foundation
import Security

protocol PendingAuthAttemptStoring {
    func createAttempt(for provider: AuthProvider) throws -> PendingAuthAttempt
    func loadAttempt(for provider: AuthProvider) throws -> PendingAuthAttempt?
    func removeAttempt(for provider: AuthProvider) throws
}

struct PendingAuthAttempt: Codable, Equatable {
    let createdAt: Date
    let privateKeyRawRepresentation: Data
    let provider: AuthProvider
    let publicKeyPEM: String
}

final class KeychainPendingAuthAttemptStore: PendingAuthAttemptStoring {
    private enum Constants {
        static let service = "com.chaoticware.discrobble.pending-auth"
    }

    private let decoder = JSONDecoder()
    private let encoder = JSONEncoder()

    init() {
        decoder.dateDecodingStrategy = .iso8601
        encoder.dateEncodingStrategy = .iso8601
    }

    func createAttempt(for provider: AuthProvider) throws -> PendingAuthAttempt {
        let privateKey = P256.KeyAgreement.PrivateKey()
        let attempt = PendingAuthAttempt(
            createdAt: Date(),
            privateKeyRawRepresentation: privateKey.rawRepresentation,
            provider: provider,
            publicKeyPEM: privateKey.publicKey.pemRepresentation
        )

        try saveAttempt(attempt)
        return attempt
    }

    func loadAttempt(for provider: AuthProvider) throws -> PendingAuthAttempt? {
        var query = baseQuery(for: provider)
        query[kSecReturnData as String] = true
        query[kSecMatchLimit as String] = kSecMatchLimitOne

        var item: CFTypeRef?
        let status = SecItemCopyMatching(query as CFDictionary, &item)

        switch status {
        case errSecSuccess:
            guard let data = item as? Data else {
                throw PendingAuthAttemptStoreError.invalidData
            }

            return try decoder.decode(PendingAuthAttempt.self, from: data)
        case errSecItemNotFound:
            return nil
        default:
            throw PendingAuthAttemptStoreError.unexpectedStatus(status)
        }
    }

    func removeAttempt(for provider: AuthProvider) throws {
        let status = SecItemDelete(baseQuery(for: provider) as CFDictionary)

        guard status == errSecSuccess || status == errSecItemNotFound else {
            throw PendingAuthAttemptStoreError.unexpectedStatus(status)
        }
    }

    private func saveAttempt(_ attempt: PendingAuthAttempt) throws {
        let data = try encoder.encode(attempt)
        var query = baseQuery(for: attempt.provider)
        query[kSecValueData as String] = data

        let status = SecItemAdd(query as CFDictionary, nil)

        switch status {
        case errSecSuccess:
            return
        case errSecDuplicateItem:
            let attributesToUpdate = [kSecValueData as String: data]
            let updateStatus = SecItemUpdate(baseQuery(for: attempt.provider) as CFDictionary, attributesToUpdate as CFDictionary)

            guard updateStatus == errSecSuccess else {
                throw PendingAuthAttemptStoreError.unexpectedStatus(updateStatus)
            }
        default:
            throw PendingAuthAttemptStoreError.unexpectedStatus(status)
        }
    }

    private func baseQuery(for provider: AuthProvider) -> [String: Any] {
        [
            kSecClass as String: kSecClassGenericPassword,
            kSecAttrAccount as String: "pending-auth.\(provider.rawValue)",
            kSecAttrAccessible as String: kSecAttrAccessibleWhenUnlockedThisDeviceOnly,
            kSecAttrService as String: Constants.service,
        ]
    }
}

enum PendingAuthAttemptStoreError: LocalizedError {
    case invalidData
    case unexpectedStatus(OSStatus)

    var errorDescription: String? {
        switch self {
        case .invalidData:
            return "The pending auth attempt could not be decoded from Keychain data."
        case let .unexpectedStatus(status):
            return "Keychain returned status \(status) while reading or writing pending auth state."
        }
    }
}
