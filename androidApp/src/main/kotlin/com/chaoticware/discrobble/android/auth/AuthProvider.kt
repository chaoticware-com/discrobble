package com.chaoticware.discrobble.android.auth

enum class AuthProvider(
    val rawValue: String,
    val displayName: String,
) {
    LASTFM(
        rawValue = "lastfm",
        displayName = "Last.fm",
    ),
    DISCOGS(
        rawValue = "discogs",
        displayName = "Discogs",
    );

    companion object {
        fun fromRawValue(rawValue: String): AuthProvider? {
            return entries.firstOrNull { provider ->
                provider.rawValue == rawValue
            }
        }
    }
}
