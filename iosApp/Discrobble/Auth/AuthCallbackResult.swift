import Foundation

enum AuthCallbackResult {
    case payload(PendingAuthCallback)
    case failure(AuthCallbackFailure)
}

struct AuthCallbackFailure: Equatable {
    let provider: AuthProvider
    let code: String
    let message: String
}
