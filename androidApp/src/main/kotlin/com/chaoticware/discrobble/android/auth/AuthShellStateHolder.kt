package com.chaoticware.discrobble.android.auth

import android.content.Intent
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.chaoticware.discrobble.android.security.StoredIntegrationTokenSet
import com.chaoticware.discrobble.android.security.TokenStore
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class AuthShellStateHolder(
    private val callbackParser: AuthCallbackParser = AuthCallbackParser(),
    private val discogsAuthClient: DiscogsAuthClient,
    private val lastfmAuthClient: LastfmAuthClient,
    private val tokenStore: TokenStore,
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

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
                    scope.launch {
                        try {
                            val tokenSet = withContext(Dispatchers.IO) {
                                when (callback.provider) {
                                    AuthProvider.LASTFM -> lastfmAuthClient.consumeCallback(callback)
                                    AuthProvider.DISCOGS -> discogsAuthClient.consumeCallback(callback)
                                }
                            }

                            applyTokenSet(tokenSet)
                        } catch (throwable: Throwable) {
                            if (throwable is CancellationException) {
                                throw throwable
                            }

                            pendingCallbacks = pendingCallbacks - callback.provider
                            authInFlightProviders = authInFlightProviders - callback.provider
                            runCatching {
                                withContext(Dispatchers.IO) {
                                    clearAttempt(callback.provider)
                                }
                            }
                            lastErrorMessage = throwable.message
                        }
                    }
                }

                is AuthCallbackResult.Failure -> {
                    pendingCallbacks = pendingCallbacks - result.failure.provider
                    authInFlightProviders = authInFlightProviders - result.failure.provider
                    lastErrorMessage = result.failure.message
                    scope.launch {
                        runCatching {
                            withContext(Dispatchers.IO) {
                                clearAttempt(result.failure.provider)
                            }
                        }
                    }
                }
            }
        }.onFailure { throwable ->
            lastErrorMessage = throwable.message
        }
    }

    suspend fun startAuth(provider: AuthProvider): String? {
        authInFlightProviders = authInFlightProviders + provider

        return try {
            val authorizeUrl = withContext(Dispatchers.IO) {
                when (provider) {
                    AuthProvider.LASTFM -> lastfmAuthClient.startAuth()
                    AuthProvider.DISCOGS -> discogsAuthClient.startAuth()
                }
            }
            lastErrorMessage = null
            authorizeUrl
        } catch (throwable: Throwable) {
            if (throwable is CancellationException) {
                throw throwable
            }

            authInFlightProviders = authInFlightProviders - provider
            lastErrorMessage = throwable.message
            null
        }
    }

    fun reloadStoredState() {
        scope.launch {
            try {
                val refreshed = withContext(Dispatchers.IO) {
                    buildMap {
                        AuthProvider.entries.forEach { provider ->
                            tokenStore.loadTokenSet(provider)?.let { tokenSet ->
                                put(provider, tokenSet)
                            }
                        }
                    }
                }

                storedTokenSets = refreshed
                lastErrorMessage = null
            } catch (throwable: Throwable) {
                if (throwable is CancellationException) {
                    throw throwable
                }

                lastErrorMessage = throwable.message
            }
        }
    }

    fun applyTokenSet(tokenSet: StoredIntegrationTokenSet) {
        scope.launch {
            try {
                withContext(Dispatchers.IO) {
                    tokenStore.saveTokenSet(tokenSet)
                }

                authInFlightProviders = authInFlightProviders - tokenSet.provider
                storedTokenSets = storedTokenSets + (tokenSet.provider to tokenSet)
                pendingCallbacks = pendingCallbacks - tokenSet.provider
                lastErrorMessage = null
            } catch (throwable: Throwable) {
                if (throwable is CancellationException) {
                    throw throwable
                }

                lastErrorMessage = throwable.message
            }
        }
    }

    fun clearStoredToken(provider: AuthProvider) {
        scope.launch {
            try {
                withContext(Dispatchers.IO) {
                    tokenStore.removeTokenSet(provider)
                }

                storedTokenSets = storedTokenSets - provider
                lastErrorMessage = null
            } catch (throwable: Throwable) {
                if (throwable is CancellationException) {
                    throw throwable
                }

                lastErrorMessage = throwable.message
            }
        }
    }

    fun clearPendingCallback(provider: AuthProvider) {
        authInFlightProviders = authInFlightProviders - provider
        pendingCallbacks = pendingCallbacks - provider
        lastErrorMessage = null
        scope.launch {
            runCatching {
                withContext(Dispatchers.IO) {
                    clearAttempt(provider)
                }
            }
        }
    }

    fun dispose() {
        scope.cancel()
    }

    private fun clearAttempt(provider: AuthProvider) {
        when (provider) {
            AuthProvider.LASTFM -> lastfmAuthClient.clearAttempt(provider)
            AuthProvider.DISCOGS -> discogsAuthClient.clearAttempt(provider)
        }
    }
}
