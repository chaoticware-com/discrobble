import Foundation

struct PendingAuthCallback: Codable, Equatable {
    let provider: AuthProvider
    let encryptedPayload: String
    let receivedAt: Date
}
