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

                    Text("iPhone auth + ShazamKit shell")
                        .font(.headline)

                    Text("This shell now starts the Last.fm and Discogs browser auth spikes, decrypts the callback handoff, persists the resulting credentials in Keychain-backed storage, and exposes a one-shot ShazamKit recognition spike on iPhone.")
                        .foregroundStyle(.secondary)
                }

                ForEach(AuthProvider.allCases) { provider in
                    ProviderStatusCard(provider: provider)
                }

                ShazamRecognitionCard()

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

private struct ShazamRecognitionCard: View {
    @EnvironmentObject private var shazamRecognitionCoordinator: ShazamRecognitionCoordinator

    var body: some View {
        VStack(alignment: .leading, spacing: 12) {
            Text("ShazamKit")
                .font(.title3)
                .bold()

            Text(shazamRecognitionCoordinator.recognitionState.statusLine)
                .font(.body)

            Text("Microphone permission: \(shazamRecognitionCoordinator.permissionStatus.displayName)")
                .font(.footnote)
                .foregroundStyle(.secondary)

            if let lastNoMatchAt = shazamRecognitionCoordinator.lastNoMatchAt {
                Text("Last no-match result: \(lastNoMatchAt.formatted(date: .abbreviated, time: .shortened))")
                    .font(.footnote)
                    .foregroundStyle(.secondary)
            }

            if let snapshot = shazamRecognitionCoordinator.lastMatch {
                VStack(alignment: .leading, spacing: 8) {
                    Text("Latest match: \(snapshot.capturedAt.formatted(date: .abbreviated, time: .shortened))")
                        .font(.footnote)
                        .foregroundStyle(.secondary)

                    ForEach(Array(snapshot.candidates.prefix(3))) { candidate in
                        VStack(alignment: .leading, spacing: 4) {
                            Text("#\(candidate.rank) \(candidate.title)")
                                .font(.headline)

                            if let artist = candidate.artist, !artist.isEmpty {
                                Text(artist)
                                    .font(.subheadline)
                            }

                            if let subtitle = candidate.subtitle, !subtitle.isEmpty {
                                Text(subtitle)
                                    .font(.footnote)
                                    .foregroundStyle(.secondary)
                            }

                            Text(candidateMetricsLine(for: candidate))
                                .font(.footnote)
                                .foregroundStyle(.secondary)

                            if !candidate.genres.isEmpty {
                                Text("Genres: \(candidate.genres.joined(separator: ", "))")
                                    .font(.footnote)
                                    .foregroundStyle(.secondary)
                            }

                            if let confidence = candidate.confidence {
                                Text("Confidence: \(formattedFloat(confidence, digits: 3))")
                                    .font(.footnote)
                                    .foregroundStyle(.secondary)
                            }

                            if let webURL = candidate.webURL {
                                Link("Open Shazam Result", destination: webURL)
                                    .font(.footnote)
                            }
                        }
                        .frame(maxWidth: .infinity, alignment: .leading)
                        .padding(12)
                        .background(.regularMaterial, in: RoundedRectangle(cornerRadius: 16, style: .continuous))
                    }
                }
            }

            Text("Observed result shape: `SHManagedSession.result()` returns `SHSession.Result`, and successful matches contain ranked `mediaItems` with title, subtitle, artist, offsets, skew, IDs, URLs, and optional confidence on iOS 18.4+.")
                .font(.footnote)
                .foregroundStyle(.secondary)

            HStack(spacing: 12) {
                Button("Listen for Match") {
                    shazamRecognitionCoordinator.startRecognition()
                }
                .disabled(shazamRecognitionCoordinator.recognitionState.isActive)

                Button("Cancel Listening") {
                    shazamRecognitionCoordinator.cancelRecognition()
                }
                .disabled(!shazamRecognitionCoordinator.recognitionState.isActive)

                Button("Refresh Permission") {
                    shazamRecognitionCoordinator.refreshPermissionStatus()
                }

                Button("Clear Last Match") {
                    shazamRecognitionCoordinator.clearLastMatch()
                }
                .disabled(
                    shazamRecognitionCoordinator.lastMatch == nil
                    && shazamRecognitionCoordinator.lastNoMatchAt == nil
                )
            }
            .buttonStyle(.bordered)
        }
        .frame(maxWidth: .infinity, alignment: .leading)
        .padding(16)
        .background(.thinMaterial, in: RoundedRectangle(cornerRadius: 20, style: .continuous))
    }

    private func candidateMetricsLine(for candidate: ShazamRecognitionCandidate) -> String {
        [
            "Match offset: \(formattedSeconds(candidate.matchOffsetSeconds))",
            "Current offset: \(formattedSeconds(candidate.predictedCurrentMatchOffsetSeconds))",
            "Skew: \(formattedFloat(candidate.frequencySkew, digits: 3))",
        ].joined(separator: " • ")
    }

    private func formattedFloat(_ value: Float, digits: Int) -> String {
        String(format: "%.\(digits)f", value)
    }

    private func formattedSeconds(_ value: TimeInterval) -> String {
        String(format: "%.2fs", value)
    }
}

#Preview {
    ContentView()
        .environmentObject(AppModel())
        .environmentObject(ShazamRecognitionCoordinator())
}
