package com.chaoticware.discrobble.android.auth

import android.content.Intent
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.chaoticware.discrobble.android.security.StoredIntegrationTokenSet
import com.chaoticware.discrobble.android.security.TokenStore

class AuthShellStateHolder(
    private val callbackParser: AuthCallbackParser = AuthCallbackParser(),
    private val tokenStore: TokenStore,
) {
    var storedTokenSets by mutableStateOf<Map<AuthProvider, StoredIntegrationTokenSet>>(emptyMap())
        private set

    var pendingCallbacks by mutableStateOf<Map<AuthProvider, PendingAuthCallback>>(emptyMap())
        private set

    var lastErrorMessage by mutableStateOf<String?>(null)
        private set

    init {
        reloadStoredState()
    }

    fun handleIncomingIntent(intent: Intent?) {
        val data = intent?.data ?: return

        runCatching {
            callbackParser.parse(data)
        }.onSuccess { callback ->
            pendingCallbacks = pendingCallbacks + (callback.provider to callback)
            lastErrorMessage = null
        }.onFailure { throwable ->
            lastErrorMessage = throwable.message
        }
    }

    fun reloadStoredState() {
        runCatching {
            buildMap {
                AuthProvider.entries.forEach { provider ->
                    tokenStore.loadTokenSet(provider)?.let { tokenSet ->
                        put(provider, tokenSet)
                    }
                }
            }
        }.onSuccess { refreshed ->
            storedTokenSets = refreshed
            lastErrorMessage = null
        }.onFailure { throwable ->
            lastErrorMessage = throwable.message
        }
    }

    fun applyTokenSet(tokenSet: StoredIntegrationTokenSet) {
        runCatching {
            tokenStore.saveTokenSet(tokenSet)
        }.onSuccess {
            storedTokenSets = storedTokenSets + (tokenSet.provider to tokenSet)
            pendingCallbacks = pendingCallbacks - tokenSet.provider
            lastErrorMessage = null
        }.onFailure { throwable ->
            lastErrorMessage = throwable.message
        }
    }

    fun clearStoredToken(provider: AuthProvider) {
        runCatching {
            tokenStore.removeTokenSet(provider)
        }.onSuccess {
            storedTokenSets = storedTokenSets - provider
            lastErrorMessage = null
        }.onFailure { throwable ->
            lastErrorMessage = throwable.message
        }
    }

    fun clearPendingCallback(provider: AuthProvider) {
        pendingCallbacks = pendingCallbacks - provider
        lastErrorMessage = null
    }
}
