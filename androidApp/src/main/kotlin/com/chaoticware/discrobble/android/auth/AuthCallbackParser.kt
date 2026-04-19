package com.chaoticware.discrobble.android.auth

import android.net.Uri

class AuthCallbackParser {
    fun parse(uri: Uri): PendingAuthCallback {
        val scheme = uri.scheme?.lowercase()
        require(scheme == "discrobble") {
            "The callback URL must use the discrobble scheme."
        }

        require(uri.host?.lowercase() == "auth") {
            "The callback URL must target discrobble://auth/lastfm or discrobble://auth/discogs."
        }

        val provider = AuthProvider.fromRawValue(uri.lastPathSegment?.lowercase().orEmpty())
            ?: throw IllegalArgumentException(
                "The callback URL must target discrobble://auth/lastfm or discrobble://auth/discogs."
            )

        val fragment = uri.fragment.orEmpty()
        val payload = Uri.parse("discrobble://fragment?$fragment").getQueryParameter("payload")
            ?.trim()
            .orEmpty()

        require(payload.isNotEmpty()) {
            "The callback URL is missing the encrypted payload fragment."
        }

        return PendingAuthCallback(
            provider = provider,
            encryptedPayload = payload,
            receivedAtMillis = System.currentTimeMillis(),
        )
    }
}
