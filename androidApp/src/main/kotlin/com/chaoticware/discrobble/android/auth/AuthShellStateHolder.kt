package com.chaoticware.discrobble.android.auth

import android.content.Intent
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.chaoticware.discrobble.android.security.StoredIntegrationTokenSet
import com.chaoticware.discrobble.android.security.TokenStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class AuthShellStateHolder(
    private val callbackParser: AuthCallbackParser = AuthCallbackParser(),
    private val lastfmAuthClient: LastfmAuthClient,
    private val tokenStore: TokenStore,
) {
    var authInFlightProviders by mutableStateOf<Set<AuthProvider>>(emptySet())
        private set

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
        }.onSuccess { result ->
            when (result) {
                is AuthCallbackResult.Payload -> {
                    val callback = result.callback
                    pendingCallbacks = pendingCallbacks + (callback.provider to callback)

                    runCatching {
                        lastfmAuthClient.consumeCallback(callback)
                    }.onSuccess { tokenSet ->
                        applyTokenSet(tokenSet)
                    }.onFailure { throwable ->
                        authInFlightProviders = authInFlightProviders - callback.provider
                        lastErrorMessage = throwable.message
                    }
                }

                is AuthCallbackResult.Failure -> {
                    pendingCallbacks = pendingCallbacks - result.failure.provider
                    authInFlightProviders = authInFlightProviders - result.failure.provider
                    lastfmAuthClient.clearAttempt(result.failure.provider)
                    lastErrorMessage = result.failure.message
                }
            }
        }.onFailure { throwable ->
            lastErrorMessage = throwable.message
        }
    }

    suspend fun startAuth(provider: AuthProvider): String? {
        if (provider != AuthProvider.LASTFM) {
            lastErrorMessage = "Discogs auth is the next integration spike."
            return null
        }

        authInFlightProviders = authInFlightProviders + provider

        return runCatching {
            withContext(Dispatchers.IO) {
                lastfmAuthClient.startAuth()
            }
        }.onSuccess {
            lastErrorMessage = null
        }.onFailure { throwable ->
            authInFlightProviders = authInFlightProviders - provider
            lastErrorMessage = throwable.message
        }.getOrNull()
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
            authInFlightProviders = authInFlightProviders - tokenSet.provider
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
        authInFlightProviders = authInFlightProviders - provider
        lastfmAuthClient.clearAttempt(provider)
        pendingCallbacks = pendingCallbacks - provider
        lastErrorMessage = null
    }
}
