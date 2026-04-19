package com.chaoticware.discrobble.android

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.chaoticware.discrobble.android.auth.AuthProvider
import com.chaoticware.discrobble.android.auth.AuthShellStateHolder
import com.chaoticware.discrobble.android.auth.PendingAuthCallback
import com.chaoticware.discrobble.android.security.StoredIntegrationTokenSet
import java.text.DateFormat
import java.util.Date

@Composable
fun DiscrobbleAndroidShell(
    stateHolder: AuthShellStateHolder,
) {
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
                        text = "Android auth shell",
                        style = MaterialTheme.typography.titleMedium,
                    )
                    Text(
                        text = "This shell accepts discrobble://auth/... callbacks and wires in Android keystore-backed token storage for the upcoming auth spike.",
                        style = MaterialTheme.typography.bodyMedium,
                    )
                }

                AuthProvider.entries.forEach { provider ->
                    ProviderStatusCard(
                        provider = provider,
                        tokenSet = stateHolder.storedTokenSets[provider],
                        pendingCallback = stateHolder.pendingCallbacks[provider],
                        onReload = stateHolder::reloadStoredState,
                        onClearStoredToken = { stateHolder.clearStoredToken(provider) },
                        onClearPendingCallback = { stateHolder.clearPendingCallback(provider) },
                    )
                }

                stateHolder.lastErrorMessage?.let { lastErrorMessage ->
                    Text(
                        text = lastErrorMessage,
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodySmall,
                    )
                }

                Text(
                    text = "Callback shape: discrobble://auth/lastfm#payload=... or discrobble://auth/discogs#payload=...",
                    style = MaterialTheme.typography.bodySmall,
                )
            }
        }
    }
}

@Composable
private fun ProviderStatusCard(
    provider: AuthProvider,
    tokenSet: StoredIntegrationTokenSet?,
    pendingCallback: PendingAuthCallback?,
    onReload: () -> Unit,
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
                        text = "Received at: ${formatTimestamp(it.receivedAtMillis)}",
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Button(onClick = onReload) {
                    Text("Reload Store")
                }

                if (tokenSet != null) {
                    Button(onClick = onClearStoredToken) {
                        Text("Clear Session")
                    }
                }

                if (pendingCallback != null) {
                    Button(onClick = onClearPendingCallback) {
                        Text(
                            text = "Clear Callback",
                            textAlign = TextAlign.Center,
                        )
                    }
                }
            }
        }
    }
}

private fun statusLine(
    tokenSet: StoredIntegrationTokenSet?,
    pendingCallback: PendingAuthCallback?,
): String {
    return when {
        tokenSet != null -> "Connected in secure storage as ${tokenSet.username}."
        pendingCallback != null -> "Auth callback received and waiting for payload decryption."
        else -> "Waiting for auth callback."
    }
}

private fun formatTimestamp(timestampMillis: Long): String {
    val formatter = DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.SHORT)
    return formatter.format(Date(timestampMillis))
}
