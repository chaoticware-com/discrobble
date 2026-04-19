import Foundation
import Security

protocol TokenStoring {
    func loadTokenSet(for provider: AuthProvider) throws -> StoredIntegrationTokenSet?
    func saveTokenSet(_ tokenSet: StoredIntegrationTokenSet) throws
    func removeTokenSet(for provider: AuthProvider) throws
}

final class KeychainTokenStore: TokenStoring {
    private enum Constants {
        static let service = "com.chaoticware.discrobble.tokens"
    }

    private let decoder = JSONDecoder()
    private let encoder = JSONEncoder()

    init() {
        decoder.dateDecodingStrategy = .iso8601
        encoder.dateEncodingStrategy = .iso8601
    }

    func loadTokenSet(for provider: AuthProvider) throws -> StoredIntegrationTokenSet? {
        var query = baseQuery(for: provider)
        query[kSecReturnData as String] = true
        query[kSecMatchLimit as String] = kSecMatchLimitOne

        var item: CFTypeRef?
        let status = SecItemCopyMatching(query as CFDictionary, &item)

        switch status {
        case errSecSuccess:
            guard let data = item as? Data else {
                throw KeychainTokenStoreError.invalidData
            }

            return try decoder.decode(StoredIntegrationTokenSet.self, from: data)
        case errSecItemNotFound:
            return nil
        default:
            throw KeychainTokenStoreError.unexpectedStatus(status)
        }
    }

    func saveTokenSet(_ tokenSet: StoredIntegrationTokenSet) throws {
        let data = try encoder.encode(tokenSet)
        var query = baseQuery(for: tokenSet.provider)
        query[kSecValueData as String] = data

        let status = SecItemAdd(query as CFDictionary, nil)

        switch status {
        case errSecSuccess:
            return
        case errSecDuplicateItem:
            let attributesToUpdate = [kSecValueData as String: data]
            let updateStatus = SecItemUpdate(baseQuery(for: tokenSet.provider) as CFDictionary, attributesToUpdate as CFDictionary)

            guard updateStatus == errSecSuccess else {
                throw KeychainTokenStoreError.unexpectedStatus(updateStatus)
            }
        default:
            throw KeychainTokenStoreError.unexpectedStatus(status)
        }
    }

    func removeTokenSet(for provider: AuthProvider) throws {
        let status = SecItemDelete(baseQuery(for: provider) as CFDictionary)

        guard status == errSecSuccess || status == errSecItemNotFound else {
            throw KeychainTokenStoreError.unexpectedStatus(status)
        }
    }

    private func baseQuery(for provider: AuthProvider) -> [String: Any] {
        [
            kSecClass as String: kSecClassGenericPassword,
            kSecAttrService as String: Constants.service,
            kSecAttrAccount as String: "integration.\(provider.rawValue)"
        ]
    }
}

enum KeychainTokenStoreError: LocalizedError {
    case invalidData
    case unexpectedStatus(OSStatus)

    var errorDescription: String? {
        switch self {
        case .invalidData:
            return "The stored token set could not be decoded from Keychain data."
        case let .unexpectedStatus(status):
            return "Keychain returned status \(status) while reading or writing integration state."
        }
    }
}
