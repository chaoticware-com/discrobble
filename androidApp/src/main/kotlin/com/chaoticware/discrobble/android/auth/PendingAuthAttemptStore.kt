package com.chaoticware.discrobble.android.auth

import android.content.Context
import android.content.SharedPreferences
import android.util.Base64
import com.chaoticware.discrobble.android.security.AndroidKeystoreCipher
import org.json.JSONObject
import java.security.KeyPairGenerator
import java.security.PublicKey
import java.security.spec.ECGenParameterSpec
import java.time.Instant

interface PendingAuthAttemptStore {
    fun createAttempt(provider: AuthProvider): PendingAuthAttempt
    fun loadAttempt(provider: AuthProvider): PendingAuthAttempt?
    fun removeAttempt(provider: AuthProvider)
}

data class PendingAuthAttempt(
    val createdAt: String,
    val privateKeyPkcs8: String,
    val provider: AuthProvider,
    val publicKeyPem: String,
)

class EncryptedPendingAuthAttemptStore(
    context: Context,
) : PendingAuthAttemptStore {
    private val cipher = AndroidKeystoreCipher(PENDING_AUTH_KEY_ALIAS)
    private val sharedPreferences: SharedPreferences = context.getSharedPreferences(
        SHARED_PREFERENCES_NAME,
        Context.MODE_PRIVATE,
    )

    override fun createAttempt(provider: AuthProvider): PendingAuthAttempt {
        val keyPairGenerator = KeyPairGenerator.getInstance("EC")

        keyPairGenerator.initialize(ECGenParameterSpec("secp256r1"))

        val keyPair = keyPairGenerator.generateKeyPair()
        val attempt = PendingAuthAttempt(
            createdAt = Instant.now().toString(),
            privateKeyPkcs8 = Base64.encodeToString(keyPair.private.encoded, Base64.NO_WRAP),
            provider = provider,
            publicKeyPem = keyPair.public.toPem(),
        )
        val encryptedPayload = cipher.encrypt(attempt.toJson().toString())

        sharedPreferences.edit()
            .putString(payloadKey(provider), encryptedPayload.ciphertext)
            .putString(ivKey(provider), encryptedPayload.iv)
            .apply()

        return attempt
    }

    override fun loadAttempt(provider: AuthProvider): PendingAuthAttempt? {
        val encryptedPayload = sharedPreferences.getString(payloadKey(provider), null) ?: return null
        val iv = sharedPreferences.getString(ivKey(provider), null) ?: return null
        val decryptedPayload = cipher.decrypt(
            encryptedPayload = encryptedPayload,
            iv = iv,
        )

        return JSONObject(decryptedPayload).toPendingAuthAttempt()
    }

    override fun removeAttempt(provider: AuthProvider) {
        sharedPreferences.edit()
            .remove(payloadKey(provider))
            .remove(ivKey(provider))
            .apply()
    }

    private fun payloadKey(provider: AuthProvider): String = "pending-auth.${provider.rawValue}.payload"

    private fun ivKey(provider: AuthProvider): String = "pending-auth.${provider.rawValue}.iv"

    private fun PendingAuthAttempt.toJson(): JSONObject {
        return JSONObject()
            .put("created_at", createdAt)
            .put("private_key_pkcs8", privateKeyPkcs8)
            .put("provider", provider.rawValue)
            .put("public_key_pem", publicKeyPem)
    }

    private fun JSONObject.toPendingAuthAttempt(): PendingAuthAttempt {
        val provider = AuthProvider.fromRawValue(optString("provider"))
            ?: throw IllegalStateException("Pending auth attempt is missing a valid provider value.")

        return PendingAuthAttempt(
            createdAt = requireString(
                field = "created_at",
                errorMessage = "Pending auth attempt is missing a valid created_at value.",
            ),
            privateKeyPkcs8 = requireString(
                field = "private_key_pkcs8",
                errorMessage = "Pending auth attempt is missing a valid private_key_pkcs8 value.",
            ),
            provider = provider,
            publicKeyPem = requireString(
                field = "public_key_pem",
                errorMessage = "Pending auth attempt is missing a valid public_key_pem value.",
            ),
        )
    }

    private fun JSONObject.requireString(
        field: String,
        errorMessage: String,
    ): String {
        return optString(field).takeIf { it.isNotBlank() }
            ?: throw IllegalStateException(errorMessage)
    }

    private fun PublicKey.toPem(): String {
        val base64 = Base64.encodeToString(encoded, Base64.NO_WRAP)
        val wrapped = base64.chunked(64).joinToString(separator = "\n")
        return "-----BEGIN PUBLIC KEY-----\n$wrapped\n-----END PUBLIC KEY-----"
    }

    private companion object {
        const val PENDING_AUTH_KEY_ALIAS = "discrobble.pending.auth"
        const val SHARED_PREFERENCES_NAME = "discrobble.pending.auth"
    }
}
