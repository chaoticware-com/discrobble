package com.chaoticware.discrobble.android.security

import com.chaoticware.discrobble.android.auth.AuthProvider

data class StoredIntegrationTokenSet(
    val provider: AuthProvider,
    val username: String,
    val accessToken: String,
    val accessSecret: String?,
    val issuedAt: String,
    val expiresAt: String?,
) {
    override fun toString(): String {
        return "StoredIntegrationTokenSet(provider=$provider, username=$username, accessToken=[REDACTED], accessSecret=[REDACTED], issuedAt=$issuedAt, expiresAt=$expiresAt)"
    }
}
