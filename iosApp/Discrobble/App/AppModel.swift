import Combine
import Foundation

@MainActor
final class AppModel: ObservableObject {
    @Published private(set) var authInFlightProviders: Set<AuthProvider> = []
    @Published private(set) var storedTokenSets: [AuthProvider: StoredIntegrationTokenSet] = [:]
    @Published private(set) var pendingCallbacks: [AuthProvider: PendingAuthCallback] = [:]
    @Published private(set) var lastErrorMessage: String?

    private let discogsAuthCoordinator: DiscogsAuthCoordinator
    private let callbackParser: AuthCallbackParser
    private let lastfmAuthCoordinator: LastfmAuthCoordinator
    private let tokenStore: TokenStoring

    init(
        discogsAuthCoordinator: DiscogsAuthCoordinator = DiscogsAuthCoordinator(),
        callbackParser: AuthCallbackParser = AuthCallbackParser(),
        lastfmAuthCoordinator: LastfmAuthCoordinator = LastfmAuthCoordinator(),
        tokenStore: TokenStoring = KeychainTokenStore()
    ) {
        self.discogsAuthCoordinator = discogsAuthCoordinator
        self.callbackParser = callbackParser
        self.lastfmAuthCoordinator = lastfmAuthCoordinator
        self.tokenStore = tokenStore
        reloadStoredState()
    }

    func handleIncomingURL(_ url: URL) {
        do {
            let result = try callbackParser.parse(url: url)

            switch result {
            case let .payload(callback):
                pendingCallbacks[callback.provider] = callback
                do {
                    let tokenSet = try consumeCallback(callback)
                    applyTokenSet(tokenSet)
                } catch {
                    authInFlightProviders.remove(callback.provider)
                    lastErrorMessage = error.localizedDescription
                }
            case let .failure(failure):
                try? clearAttempt(for: failure.provider)
                pendingCallbacks[failure.provider] = nil
                authInFlightProviders.remove(failure.provider)
                lastErrorMessage = failure.message
            }
        } catch {
            lastErrorMessage = error.localizedDescription
        }
    }

    func startAuth(for provider: AuthProvider) async -> URL? {
        authInFlightProviders.insert(provider)

        do {
            let authorizeURL: URL

            switch provider {
            case .lastfm:
                authorizeURL = try await lastfmAuthCoordinator.startAuth()
            case .discogs:
                authorizeURL = try discogsAuthCoordinator.startAuth()
            }

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
            authInFlightProviders.remove(tokenSet.provider)
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
        try? clearAttempt(for: provider)
        lastErrorMessage = nil
    }

    private func clearAttempt(for provider: AuthProvider) throws {
        switch provider {
        case .lastfm:
            try lastfmAuthCoordinator.clearAttempt(for: provider)
        case .discogs:
            try discogsAuthCoordinator.clearAttempt(for: provider)
        }
    }

    private func consumeCallback(_ callback: PendingAuthCallback) throws -> StoredIntegrationTokenSet {
        switch callback.provider {
        case .lastfm:
            return try lastfmAuthCoordinator.consumeCallback(callback)
        case .discogs:
            return try discogsAuthCoordinator.consumeCallback(callback)
        }
    }
}
