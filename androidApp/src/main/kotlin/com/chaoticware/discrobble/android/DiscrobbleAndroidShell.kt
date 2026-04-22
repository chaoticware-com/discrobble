package com.chaoticware.discrobble.android

import android.Manifest
import android.content.Intent
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.chaoticware.discrobble.android.auth.AuthProvider
import com.chaoticware.discrobble.android.auth.AuthShellStateHolder
import com.chaoticware.discrobble.android.auth.PendingAuthCallback
import com.chaoticware.discrobble.android.recognition.ShazamMicrophonePermissionStatus
import com.chaoticware.discrobble.android.recognition.ShazamRecognitionCandidate
import com.chaoticware.discrobble.android.recognition.ShazamRecognitionStateHolder
import com.chaoticware.discrobble.android.security.StoredIntegrationTokenSet
import java.text.DateFormat
import java.util.Date
import kotlinx.coroutines.launch

@Composable
fun DiscrobbleAndroidShell(
    authStateHolder: AuthShellStateHolder,
    shazamStateHolder: ShazamRecognitionStateHolder,
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    val uriHandler = LocalUriHandler.current
    val microphonePermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission(),
    ) { granted ->
        shazamStateHolder.onMicrophonePermissionResult(
            granted = granted,
            startRecognitionWhenGranted = granted,
        )
    }

    MaterialTheme {
        Surface(modifier = Modifier.fillMaxSize()) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(24.dp),
                verticalArrangement = Arrangement.spacedBy(24.dp),
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        text = "Discrobble",
                        style = MaterialTheme.typography.headlineMedium,
                    )
                    Text(
                        text = "Android auth + ShazamKit shell",
                        style = MaterialTheme.typography.titleMedium,
                    )
                    Text(
                        text = "This shell now starts the Last.fm and Discogs browser auth spikes, decrypts the callback handoff, persists the resulting credentials in keystore-backed storage, and exposes a one-shot Android ShazamKit spike that activates when Apple's local AAR and a developer token are present.",
                        style = MaterialTheme.typography.bodyMedium,
                    )
                }

                AuthProvider.entries.forEach { provider ->
                    ProviderStatusCard(
                        provider = provider,
                        isAuthInFlight = authStateHolder.authInFlightProviders.contains(provider),
                        tokenSet = authStateHolder.storedTokenSets[provider],
                        pendingCallback = authStateHolder.pendingCallbacks[provider],
                        onReload = authStateHolder::reloadStoredState,
                        onStartAuth = {
                            coroutineScope.launch {
                                val authorizeUrl = authStateHolder.startAuth(provider) ?: return@launch
                                context.startActivity(
                                    Intent(Intent.ACTION_VIEW, Uri.parse(authorizeUrl)),
                                )
                            }
                        },
                        onClearStoredToken = { authStateHolder.clearStoredToken(provider) },
                        onClearPendingCallback = { authStateHolder.clearPendingCallback(provider) },
                    )
                }

                ShazamRecognitionCard(
                    stateHolder = shazamStateHolder,
                    onStartRecognition = {
                        if (shazamStateHolder.permissionStatus == ShazamMicrophonePermissionStatus.GRANTED) {
                            shazamStateHolder.startRecognition()
                        } else {
                            microphonePermissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
                        }
                    },
                    onOpenUrl = { url -> uriHandler.openUri(url) },
                )

                authStateHolder.lastErrorMessage?.let { lastErrorMessage ->
                    Text(
                        text = lastErrorMessage,
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodySmall,
                    )
                }

                Text(
                    text = "Callback shape: discrobble://auth/{provider}#payload=... on success or #error_code=... on failure.",
                    style = MaterialTheme.typography.bodySmall,
                )
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ProviderStatusCard(
    provider: AuthProvider,
    isAuthInFlight: Boolean,
    tokenSet: StoredIntegrationTokenSet?,
    pendingCallback: PendingAuthCallback?,
    onReload: () -> Unit,
    onStartAuth: () -> Unit,
    onClearStoredToken: () -> Unit,
    onClearPendingCallback: () -> Unit,
) {
    Card {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(
                text = provider.displayName,
                style = MaterialTheme.typography.titleMedium,
            )

            Text(
                text = statusLine(
                    isAuthInFlight = isAuthInFlight,
                    provider = provider,
                    tokenSet = tokenSet,
                    pendingCallback = pendingCallback,
                ),
                style = MaterialTheme.typography.bodyMedium,
            )

            tokenSet?.let {
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(
                        text = "Stored username: ${it.username}",
                        style = MaterialTheme.typography.bodySmall,
                    )
                    Text(
                        text = "Stored at: ${it.issuedAt}",
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
            }

            pendingCallback?.let {
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(
                        text = "Pending encrypted payload received.",
                        style = MaterialTheme.typography.bodySmall,
                    )
                    Text(
                        text = "Received at: ${formatTimestamp(it.receivedAtEpochMillis)}",
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
            }

            Button(
                modifier = Modifier.fillMaxWidth(),
                enabled = !isAuthInFlight,
                onClick = onStartAuth,
            ) {
                Text(
                    text = if (tokenSet == null) {
                        "Connect ${provider.displayName}"
                    } else {
                        "Reconnect ${provider.displayName}"
                    },
                )
            }

            FlowRow(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                if (tokenSet != null) {
                    Button(
                        modifier = Modifier.widthIn(min = 160.dp),
                        onClick = onClearStoredToken,
                    ) {
                        Text("Clear Credentials")
                    }
                }

                if (pendingCallback != null) {
                    Button(
                        modifier = Modifier.widthIn(min = 160.dp),
                        onClick = onClearPendingCallback,
                    ) {
                        Text(
                            text = "Clear Callback",
                            textAlign = TextAlign.Center,
                        )
                    }
                }

                Button(
                    modifier = Modifier.widthIn(min = 160.dp),
                    onClick = onReload,
                ) {
                    Text("Reload Store")
                }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ShazamRecognitionCard(
    stateHolder: ShazamRecognitionStateHolder,
    onStartRecognition: () -> Unit,
    onOpenUrl: (String) -> Unit,
) {
    Card {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(
                text = "ShazamKit",
                style = MaterialTheme.typography.titleMedium,
            )

            Text(
                text = stateHolder.recognitionState.statusLine,
                style = MaterialTheme.typography.bodyMedium,
            )

            Text(
                text = "Microphone permission: ${stateHolder.permissionStatus.displayName}",
                style = MaterialTheme.typography.bodySmall,
            )

            stateHolder.lastNoMatchAtMillis?.let { lastNoMatchAtMillis ->
                Text(
                    text = "Last no-match result: ${formatTimestamp(lastNoMatchAtMillis)}",
                    style = MaterialTheme.typography.bodySmall,
                )
            }

            stateHolder.lastMatch?.let { snapshot ->
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        text = "Latest match: ${formatTimestamp(snapshot.capturedAtMillis)}",
                        style = MaterialTheme.typography.bodySmall,
                    )

                    snapshot.candidates.take(3).forEach { candidate ->
                        CandidateCard(
                            candidate = candidate,
                            onOpenUrl = onOpenUrl,
                        )
                    }
                }
            }

            Text(
                text = stateHolder.resultShapeSummary,
                style = MaterialTheme.typography.bodySmall,
            )

            Button(
                modifier = Modifier.fillMaxWidth(),
                enabled = !stateHolder.recognitionState.isActive,
                onClick = onStartRecognition,
            ) {
                Text(
                    text = if (stateHolder.permissionStatus == ShazamMicrophonePermissionStatus.GRANTED) {
                        "Listen for Match"
                    } else {
                        "Grant Mic + Listen"
                    },
                )
            }

            FlowRow(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Button(
                    modifier = Modifier.widthIn(min = 160.dp),
                    enabled = stateHolder.recognitionState.isActive,
                    onClick = stateHolder::cancelRecognition,
                ) {
                    Text("Cancel Listening")
                }

                Button(
                    modifier = Modifier.widthIn(min = 160.dp),
                    onClick = stateHolder::refreshPermissionStatus,
                ) {
                    Text("Refresh Permission")
                }

                Button(
                    modifier = Modifier.widthIn(min = 160.dp),
                    enabled = stateHolder.lastMatch != null || stateHolder.lastNoMatchAtMillis != null,
                    onClick = stateHolder::clearLastMatch,
                ) {
                    Text("Clear Last Match")
                }
            }
        }
    }
}

@Composable
private fun CandidateCard(
    candidate: ShazamRecognitionCandidate,
    onOpenUrl: (String) -> Unit,
) {
    Card {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Text(
                text = "#${candidate.rank} ${candidate.title}",
                style = MaterialTheme.typography.titleSmall,
            )

            candidate.artist?.takeIf { it.isNotBlank() }?.let { artist ->
                Text(
                    text = artist,
                    style = MaterialTheme.typography.bodyMedium,
                )
            }

            candidate.subtitle?.takeIf { it.isNotBlank() }?.let { subtitle ->
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodySmall,
                )
            }

            Text(
                text = candidateMetricsLine(candidate),
                style = MaterialTheme.typography.bodySmall,
            )

            if (candidate.genres.isNotEmpty()) {
                Text(
                    text = "Genres: ${candidate.genres.joinToString()}",
                    style = MaterialTheme.typography.bodySmall,
                )
            }

            if (candidate.isrc != null || candidate.explicitContent != null) {
                Text(
                    text = listOfNotNull(
                        candidate.isrc?.let { "ISRC: $it" },
                        candidate.explicitContent?.let { "Explicit: ${if (it) "Yes" else "No"}" },
                    ).joinToString(" • "),
                    style = MaterialTheme.typography.bodySmall,
                )
            }

            Text(
                text = "Time ranges: ${candidate.timeRangeCount} • Skew ranges: ${candidate.frequencySkewRangeCount}",
                style = MaterialTheme.typography.bodySmall,
            )

            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                candidate.webUrl?.let { webUrl ->
                    TextButton(onClick = { onOpenUrl(webUrl) }) {
                        Text("Open Result")
                    }
                }

                candidate.appleMusicUrl?.let { appleMusicUrl ->
                    TextButton(onClick = { onOpenUrl(appleMusicUrl) }) {
                        Text("Open Apple Music")
                    }
                }
            }
        }
    }
}

private fun statusLine(
    isAuthInFlight: Boolean,
    provider: AuthProvider,
    tokenSet: StoredIntegrationTokenSet?,
    pendingCallback: PendingAuthCallback?,
): String {
    return when {
        tokenSet != null -> "Connected in secure storage as ${tokenSet.username}."
        isAuthInFlight -> "Waiting for the ${provider.displayName} browser approval callback."
        pendingCallback != null -> "Encrypted callback received and waiting for secure handoff processing."
        else -> "Ready to start the ${provider.displayName} browser auth flow."
    }
}

private fun candidateMetricsLine(candidate: ShazamRecognitionCandidate): String {
    val metrics = buildList {
        add("Match offset: ${formatSeconds(candidate.matchOffsetInMs)}")
        add("Current offset: ${formatSeconds(candidate.predictedCurrentMatchOffsetInMs)}")
        add("Skew: ${formatFloat(candidate.frequencySkew)}")
        candidate.shazamId?.let { add("Shazam ID: $it") }
        candidate.appleMusicId?.let { add("Apple Music ID: $it") }
    }

    return metrics.joinToString(" • ")
}

private fun formatFloat(value: Float?): String {
    return if (value == null) {
        "n/a"
    } else {
        "%.3f".format(value)
    }
}

private fun formatSeconds(valueInMs: Float?): String {
    return if (valueInMs == null) {
        "n/a"
    } else {
        "%.2fs".format(valueInMs / 1_000f)
    }
}

private fun formatTimestamp(timestampMillis: Long): String {
    val formatter = DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.SHORT)
    return formatter.format(Date(timestampMillis))
}
