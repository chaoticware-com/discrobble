package com.chaoticware.discrobble.android.auth

sealed interface AuthCallbackResult {
    data class Payload(
        val callback: PendingAuthCallback,
    ) : AuthCallbackResult

    data class Failure(
        val failure: AuthCallbackFailure,
    ) : AuthCallbackResult
}

data class AuthCallbackFailure(
    val provider: AuthProvider,
    val code: String,
    val message: String,
)
