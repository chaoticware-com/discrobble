package com.chaoticware.discrobble.android.security

import android.content.Context
import android.content.SharedPreferences
import com.chaoticware.discrobble.android.auth.AuthProvider
import org.json.JSONObject

interface TokenStore {
    fun loadTokenSet(provider: AuthProvider): StoredIntegrationTokenSet?
    fun saveTokenSet(tokenSet: StoredIntegrationTokenSet)
    fun removeTokenSet(provider: AuthProvider)
}

class AndroidKeystoreTokenStore(
    context: Context,
) : TokenStore {
    private val cipher = AndroidKeystoreCipher(KEY_ALIAS)
    private val sharedPreferences: SharedPreferences = context.getSharedPreferences(
        SHARED_PREFERENCES_NAME,
        Context.MODE_PRIVATE,
    )

    override fun loadTokenSet(provider: AuthProvider): StoredIntegrationTokenSet? {
        val encryptedPayload = sharedPreferences.getString(payloadKey(provider), null) ?: return null
        val iv = sharedPreferences.getString(ivKey(provider), null) ?: return null
        val decryptedPayload = cipher.decrypt(
            encryptedPayload = encryptedPayload,
            iv = iv,
        )

        return JSONObject(decryptedPayload).toStoredIntegrationTokenSet()
    }

    override fun saveTokenSet(tokenSet: StoredIntegrationTokenSet) {
        val jsonPayload = tokenSet.toJson().toString()
        val encryptedPayload = cipher.encrypt(jsonPayload)

        sharedPreferences.edit()
            .putString(payloadKey(tokenSet.provider), encryptedPayload.ciphertext)
            .putString(ivKey(tokenSet.provider), encryptedPayload.iv)
            .apply()
    }

    override fun removeTokenSet(provider: AuthProvider) {
        sharedPreferences.edit()
            .remove(payloadKey(provider))
            .remove(ivKey(provider))
            .apply()
    }

    private fun payloadKey(provider: AuthProvider): String = "integration.${provider.rawValue}.payload"

    private fun ivKey(provider: AuthProvider): String = "integration.${provider.rawValue}.iv"

    private fun StoredIntegrationTokenSet.toJson(): JSONObject {
        return JSONObject()
            .put("provider", provider.rawValue)
            .put("username", username)
            .put("access_token", accessToken)
            .put("access_secret", accessSecret)
            .put("issued_at", issuedAt)
            .put("expires_at", expiresAt)
    }

    private fun JSONObject.toStoredIntegrationTokenSet(): StoredIntegrationTokenSet {
        val provider = AuthProvider.fromRawValue(optString("provider"))
            ?: throw IllegalStateException("Stored token payload is missing a valid provider value.")
        val username = requiredString("username")
        val accessToken = requiredString("access_token")
        val issuedAt = requiredString("issued_at")

        return StoredIntegrationTokenSet(
            provider = provider,
            username = username,
            accessToken = accessToken,
            accessSecret = optString("access_secret").takeUnless { it.isEmpty() || it == "null" },
            issuedAt = issuedAt,
            expiresAt = optString("expires_at").takeUnless { it.isEmpty() || it == "null" },
        )
    }

    private fun JSONObject.requiredString(key: String): String {
        return optString(key)
            .takeUnless { it.isBlank() || it == "null" }
            ?: throw IllegalStateException("Stored token payload is missing a valid $key value.")
    }

    private companion object {
        const val KEY_ALIAS = "discrobble.integration.tokens"
        const val SHARED_PREFERENCES_NAME = "discrobble.secure.tokens"
    }
}
