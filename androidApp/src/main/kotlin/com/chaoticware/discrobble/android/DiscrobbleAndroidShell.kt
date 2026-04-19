package com.chaoticware.discrobble.android

import android.content.Intent
import android.net.Uri
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
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.chaoticware.discrobble.android.auth.AuthProvider
import com.chaoticware.discrobble.android.auth.AuthShellStateHolder
import com.chaoticware.discrobble.android.auth.PendingAuthCallback
import com.chaoticware.discrobble.android.security.StoredIntegrationTokenSet
import java.text.DateFormat
import java.util.Date
import kotlinx.coroutines.launch

@Composable
fun DiscrobbleAndroidShell(
    stateHolder: AuthShellStateHolder,
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()

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
                        text = "This shell now starts the Last.fm and Discogs browser auth spikes, decrypts the callback handoff, and persists the resulting credentials in keystore-backed storage.",
                        style = MaterialTheme.typography.bodyMedium,
                    )
                }

                AuthProvider.entries.forEach { provider ->
                    ProviderStatusCard(
                        provider = provider,
                        isAuthInFlight = stateHolder.authInFlightProviders.contains(provider),
                        tokenSet = stateHolder.storedTokenSets[provider],
                        pendingCallback = stateHolder.pendingCallbacks[provider],
                        onReload = stateHolder::reloadStoredState,
                        onStartAuth = {
                            coroutineScope.launch {
                                val authorizeUrl = stateHolder.startAuth(provider) ?: return@launch
                                context.startActivity(
                                    Intent(Intent.ACTION_VIEW, Uri.parse(authorizeUrl)),
                                )
                            }
                        },
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
                    text = "Callback shape: discrobble://auth/{provider}#payload=... on success or #error_code=... on failure.",
                    style = MaterialTheme.typography.bodySmall,
                )
            }
        }
    }
}

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
                        text = "Received at: ${formatTimestamp(it.receivedAtMillis)}",
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Button(
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

                if (tokenSet != null) {
                    Button(onClick = onClearStoredToken) {
                        Text("Clear Credentials")
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

                Button(onClick = onReload) {
                    Text("Reload Store")
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

private fun formatTimestamp(timestampMillis: Long): String {
    val formatter = DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.SHORT)
    return formatter.format(Date(timestampMillis))
}
