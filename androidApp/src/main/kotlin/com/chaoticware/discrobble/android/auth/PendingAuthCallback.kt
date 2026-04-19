package com.chaoticware.discrobble.android.auth

data class PendingAuthCallback(
    val provider: AuthProvider,
    val encryptedPayload: String,
    val receivedAtMillis: Long,
)
