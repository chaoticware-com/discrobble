import SwiftUI

struct ContentView: View {
    @EnvironmentObject private var appModel: AppModel
    @Environment(\.openURL) private var openURL

    var body: some View {
        ScrollView {
            VStack(alignment: .leading, spacing: 24) {
                VStack(alignment: .leading, spacing: 8) {
                    Text("Discrobble")
                        .font(.largeTitle)
                        .bold()

                    Text("iPhone auth shell")
                        .font(.headline)

                    Text("This shell now starts the Last.fm and Discogs browser auth spikes, decrypts the callback handoff, and persists the resulting credentials in Keychain-backed storage.")
                        .foregroundStyle(.secondary)
                }

                ForEach(AuthProvider.allCases) { provider in
                    ProviderStatusCard(provider: provider)
                }

                if let lastErrorMessage = appModel.lastErrorMessage {
                    Text(lastErrorMessage)
                        .font(.footnote)
                        .foregroundStyle(.red)
                }

                Text("Callback shape: `discrobble://auth/{provider}#payload=...` on success or `#error_code=...` on failure.")
                    .font(.footnote)
                    .foregroundStyle(.secondary)
            }
            .frame(maxWidth: .infinity, alignment: .leading)
            .padding(24)
        }
    }
}

private struct ProviderStatusCard: View {
    @EnvironmentObject private var appModel: AppModel
    @Environment(\.openURL) private var openURL

    let provider: AuthProvider

    private var tokenSet: StoredIntegrationTokenSet? {
        appModel.storedTokenSets[provider]
    }

    private var pendingCallback: PendingAuthCallback? {
        appModel.pendingCallbacks[provider]
    }

    private var isAuthInFlight: Bool {
        appModel.authInFlightProviders.contains(provider)
    }

    var body: some View {
        VStack(alignment: .leading, spacing: 12) {
            Text(provider.displayName)
                .font(.title3)
                .bold()

            Text(statusLine)
                .font(.body)

            if let tokenSet {
                VStack(alignment: .leading, spacing: 4) {
                    Text("Stored username: \(tokenSet.username)")
                    Text("Stored at: \(tokenSet.issuedAt.formatted(date: .abbreviated, time: .shortened))")
                }
                .font(.footnote)
                .foregroundStyle(.secondary)
            }

            if let pendingCallback {
                VStack(alignment: .leading, spacing: 4) {
                    Text("Pending encrypted payload received.")
                    Text("Received at: \(pendingCallback.receivedAt.formatted(date: .abbreviated, time: .shortened))")
                }
                .font(.footnote)
                .foregroundStyle(.secondary)
            }

            HStack(spacing: 12) {
                Button(tokenSet == nil ? "Connect \(provider.displayName)" : "Reconnect \(provider.displayName)") {
                    Task {
                        if let authorizeURL = await appModel.startAuth(for: provider) {
                            openURL(authorizeURL)
                        }
                    }
                }
                .disabled(isAuthInFlight)

                if tokenSet != nil {
                    Button("Clear Stored Credentials") {
                        appModel.clearStoredToken(for: provider)
                    }
                }

                if pendingCallback != nil {
                    Button("Clear Pending Callback") {
                        appModel.clearPendingCallback(for: provider)
                    }
                }

                Button("Reload Keychain") {
                    appModel.reloadStoredState()
                }
            }
            .buttonStyle(.bordered)
        }
        .frame(maxWidth: .infinity, alignment: .leading)
        .padding(16)
        .background(.thinMaterial, in: RoundedRectangle(cornerRadius: 20, style: .continuous))
    }

    private var statusLine: String {
        if let tokenSet {
            return "Connected in secure storage as \(tokenSet.username)."
        }

        if isAuthInFlight {
            return "Waiting for the \(provider.displayName) browser approval callback."
        }

        if pendingCallback != nil {
            return "Encrypted callback received and waiting for secure handoff processing."
        }

        return "Ready to start the \(provider.displayName) browser auth flow."
    }
}

#Preview {
    ContentView()
        .environmentObject(AppModel())
}
