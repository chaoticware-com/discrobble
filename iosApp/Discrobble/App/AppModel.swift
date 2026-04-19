import Combine
import Foundation

@MainActor
final class AppModel: ObservableObject {
    @Published private(set) var storedTokenSets: [AuthProvider: StoredIntegrationTokenSet] = [:]
    @Published private(set) var pendingCallbacks: [AuthProvider: PendingAuthCallback] = [:]
    @Published private(set) var lastErrorMessage: String?

    private let callbackParser: AuthCallbackParser
    private let tokenStore: TokenStoring

    init(
        callbackParser: AuthCallbackParser = AuthCallbackParser(),
        tokenStore: TokenStoring = KeychainTokenStore()
    ) {
        self.callbackParser = callbackParser
        self.tokenStore = tokenStore
        reloadStoredState()
    }

    func handleIncomingURL(_ url: URL) {
        do {
            let callback = try callbackParser.parse(url: url)
            pendingCallbacks[callback.provider] = callback
            lastErrorMessage = nil
        } catch {
            lastErrorMessage = error.localizedDescription
        }
    }

    func reloadStoredState() {
        var refreshed: [AuthProvider: StoredIntegrationTokenSet] = [:]

        do {
            for provider in AuthProvider.allCases {
                refreshed[provider] = try tokenStore.loadTokenSet(for: provider)
            }

            storedTokenSets = refreshed
            lastErrorMessage = nil
        } catch {
            lastErrorMessage = error.localizedDescription
        }
    }

    func applyTokenSet(_ tokenSet: StoredIntegrationTokenSet) {
        do {
            try tokenStore.saveTokenSet(tokenSet)
            storedTokenSets[tokenSet.provider] = tokenSet
            pendingCallbacks[tokenSet.provider] = nil
            lastErrorMessage = nil
        } catch {
            lastErrorMessage = error.localizedDescription
        }
    }

    func clearStoredToken(for provider: AuthProvider) {
        do {
            try tokenStore.removeTokenSet(for: provider)
            storedTokenSets[provider] = nil
            lastErrorMessage = nil
        } catch {
            lastErrorMessage = error.localizedDescription
        }
    }

    func clearPendingCallback(for provider: AuthProvider) {
        pendingCallbacks[provider] = nil
        lastErrorMessage = nil
    }
}
