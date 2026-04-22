package com.chaoticware.discrobble.android.auth

import android.net.Uri

class AuthCallbackParser {
    fun parse(uri: Uri): AuthCallbackResult {
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

        val encodedFragment = uri.encodedFragment.orEmpty()

        if (encodedFragment.isNotEmpty() && !encodedFragment.contains('=')) {
            return invalidCallbackFailure(
                provider = provider,
                message = "The callback URL fragment was not encoded as expected.",
            )
        }

        // The Worker encodes callback fragments as query-string pairs such as
        // #payload=... or #error_code=...&error_message=..., so re-parse the
        // encoded fragment as a synthetic query URI before reading keys.
        val fragmentUri = Uri.parse("discrobble://fragment?$encodedFragment")
        val errorCode = fragmentUri.getQueryParameter("error_code")?.trim().orEmpty()

        if (errorCode.isNotEmpty()) {
            val errorMessage = fragmentUri.getQueryParameter("error_message")
                ?.trim()
                .takeUnless { it.isNullOrEmpty() }
                ?: "The provider auth flow did not complete successfully."

            return AuthCallbackResult.Failure(
                failure = AuthCallbackFailure(
                    provider = provider,
                    code = errorCode,
                    message = errorMessage,
                ),
            )
        }

        val payload = fragmentUri.getQueryParameter("payload")
            ?.trim()
            .orEmpty()

        if (payload.isEmpty()) {
            return invalidCallbackFailure(
                provider = provider,
                message = "The callback URL is missing the encrypted payload fragment.",
            )
        }

        return AuthCallbackResult.Payload(
            callback = PendingAuthCallback(
                provider = provider,
                encryptedPayload = payload,
                receivedAtEpochMillis = System.currentTimeMillis(),
            ),
        )
    }

    private fun invalidCallbackFailure(
        provider: AuthProvider,
        message: String,
    ): AuthCallbackResult {
        return AuthCallbackResult.Failure(
            failure = AuthCallbackFailure(
                provider = provider,
                code = "invalid_callback",
                message = message,
            ),
        )
    }
}
