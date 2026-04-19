import SwiftUI

struct ContentView: View {
    @EnvironmentObject private var appModel: AppModel

    var body: some View {
        ScrollView {
            VStack(alignment: .leading, spacing: 24) {
                VStack(alignment: .leading, spacing: 8) {
                    Text("Discrobble")
                        .font(.largeTitle)
                        .bold()

                    Text("iPhone auth shell")
                        .font(.headline)

                    Text("This shell accepts `discrobble://auth/...` callbacks and wires in Keychain-backed token storage for the upcoming auth spike.")
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

                Text("Callback shape: `discrobble://auth/lastfm#payload=...` or `discrobble://auth/discogs#payload=...`.")
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

    let provider: AuthProvider

    private var tokenSet: StoredIntegrationTokenSet? {
        appModel.storedTokenSets[provider]
    }

    private var pendingCallback: PendingAuthCallback? {
        appModel.pendingCallbacks[provider]
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
                Button("Reload Keychain") {
                    appModel.reloadStoredState()
                }

                if tokenSet != nil {
                    Button("Clear Stored Session") {
                        appModel.clearStoredToken(for: provider)
                    }
                }

                if pendingCallback != nil {
                    Button("Clear Pending Callback") {
                        appModel.clearPendingCallback(for: provider)
                    }
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

        if pendingCallback != nil {
            return "Auth callback received and waiting for payload decryption."
        }

        return "Waiting for auth callback."
    }
}

#Preview {
    ContentView()
        .environmentObject(AppModel())
}
