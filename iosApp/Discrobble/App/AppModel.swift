import Combine
import Foundation

@MainActor
final class AppModel: ObservableObject {
    @Published private(set) var authInFlightProviders: Set<AuthProvider> = []
    @Published private(set) var storedTokenSets: [AuthProvider: StoredIntegrationTokenSet] = [:]
    @Published private(set) var pendingCallbacks: [AuthProvider: PendingAuthCallback] = [:]
    @Published private(set) var lastErrorMessage: String?

    private let authCoordinator: LastfmAuthCoordinator
    private let callbackParser: AuthCallbackParser
    private let tokenStore: TokenStoring

    init(
        authCoordinator: LastfmAuthCoordinator = LastfmAuthCoordinator(),
        callbackParser: AuthCallbackParser = AuthCallbackParser(),
        tokenStore: TokenStoring = KeychainTokenStore()
    ) {
        self.authCoordinator = authCoordinator
        self.callbackParser = callbackParser
        self.tokenStore = tokenStore
        reloadStoredState()
    }

    func handleIncomingURL(_ url: URL) {
        do {
            let result = try callbackParser.parse(url: url)

            switch result {
            case let .payload(callback):
                pendingCallbacks[callback.provider] = callback
                let tokenSet = try authCoordinator.consumeCallback(callback)
                applyTokenSet(tokenSet)
                authInFlightProviders.remove(callback.provider)
            case let .failure(failure):
                try? authCoordinator.clearAttempt(for: failure.provider)
                pendingCallbacks[failure.provider] = nil
                authInFlightProviders.remove(failure.provider)
                lastErrorMessage = failure.message
            }
        } catch {
            lastErrorMessage = error.localizedDescription
        }
    }

    func startAuth(for provider: AuthProvider) async -> URL? {
        guard provider == .lastfm else {
            lastErrorMessage = "Discogs auth is the next integration spike."
            return nil
        }

        authInFlightProviders.insert(provider)

        do {
            let authorizeURL = try await authCoordinator.startAuth()
            lastErrorMessage = nil
            return authorizeURL
        } catch {
            authInFlightProviders.remove(provider)
            lastErrorMessage = error.localizedDescription
            return nil
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
        authInFlightProviders.remove(provider)
        try? authCoordinator.clearAttempt(for: provider)
        lastErrorMessage = nil
    }
}
