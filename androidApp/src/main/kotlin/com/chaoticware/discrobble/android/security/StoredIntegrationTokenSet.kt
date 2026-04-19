package com.chaoticware.discrobble.android.security

import com.chaoticware.discrobble.android.auth.AuthProvider

data class StoredIntegrationTokenSet(
    val provider: AuthProvider,
    val username: String,
    val accessToken: String,
    val accessSecret: String?,
    val issuedAt: String,
    val expiresAt: String?,
)
