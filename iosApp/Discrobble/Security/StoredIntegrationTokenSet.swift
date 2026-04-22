import Foundation

struct StoredIntegrationTokenSet: Codable, Equatable {
    let provider: AuthProvider
    let username: String
    let accessToken: String
    let accessSecret: String?
    let issuedAt: Date
    let expiresAt: Date?

    enum CodingKeys: String, CodingKey {
        case provider
        case username
        case accessToken = "access_token"
        case accessSecret = "access_secret"
        case issuedAt = "issued_at"
        case expiresAt = "expires_at"
    }
}
