package com.chaoticware.discrobble.android.security

import android.content.Context
import android.content.SharedPreferences
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import com.chaoticware.discrobble.android.auth.AuthProvider
import org.json.JSONObject
import java.nio.charset.StandardCharsets
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

interface TokenStore {
    fun loadTokenSet(provider: AuthProvider): StoredIntegrationTokenSet?
    fun saveTokenSet(tokenSet: StoredIntegrationTokenSet)
    fun removeTokenSet(provider: AuthProvider)
}

class AndroidKeystoreTokenStore(
    context: Context,
) : TokenStore {
    private val sharedPreferences: SharedPreferences = context.getSharedPreferences(
        SHARED_PREFERENCES_NAME,
        Context.MODE_PRIVATE,
    )

    override fun loadTokenSet(provider: AuthProvider): StoredIntegrationTokenSet? {
        val encryptedPayload = sharedPreferences.getString(payloadKey(provider), null) ?: return null
        val iv = sharedPreferences.getString(ivKey(provider), null) ?: return null
        val decryptedPayload = decrypt(
            encryptedPayload = encryptedPayload,
            iv = iv,
        )

        return JSONObject(decryptedPayload).toStoredIntegrationTokenSet()
    }

    override fun saveTokenSet(tokenSet: StoredIntegrationTokenSet) {
        val jsonPayload = tokenSet.toJson().toString()
        val encryptedPayload = encrypt(jsonPayload)

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

    private fun encrypt(plainText: String): EncryptedPayload {
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.ENCRYPT_MODE, getOrCreateSecretKey())
        val encrypted = cipher.doFinal(plainText.toByteArray(StandardCharsets.UTF_8))

        return EncryptedPayload(
            ciphertext = Base64.encodeToString(encrypted, Base64.NO_WRAP),
            iv = Base64.encodeToString(cipher.iv, Base64.NO_WRAP),
        )
    }

    private fun decrypt(
        encryptedPayload: String,
        iv: String,
    ): String {
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(
            Cipher.DECRYPT_MODE,
            getOrCreateSecretKey(),
            GCMParameterSpec(
                GCM_TAG_LENGTH,
                Base64.decode(iv, Base64.NO_WRAP),
            ),
        )
        val decrypted = cipher.doFinal(Base64.decode(encryptedPayload, Base64.NO_WRAP))
        return decrypted.toString(StandardCharsets.UTF_8)
    }

    private fun getOrCreateSecretKey(): SecretKey {
        val keyStore = KeyStore.getInstance(ANDROID_KEYSTORE).apply {
            load(null)
        }

        val existingKey = keyStore.getKey(KEY_ALIAS, null) as? SecretKey
        if (existingKey != null) {
            return existingKey
        }

        val keyGenerator = KeyGenerator.getInstance(
            KeyProperties.KEY_ALGORITHM_AES,
            ANDROID_KEYSTORE,
        )
        val parameterSpec = KeyGenParameterSpec.Builder(
            KEY_ALIAS,
            KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT,
        )
            .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
            .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
            .setKeySize(AES_KEY_SIZE)
            .build()

        keyGenerator.init(parameterSpec)
        return keyGenerator.generateKey()
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

        return StoredIntegrationTokenSet(
            provider = provider,
            username = optString("username"),
            accessToken = optString("access_token"),
            accessSecret = optString("access_secret").takeUnless { it.isEmpty() || it == "null" },
            issuedAt = optString("issued_at"),
            expiresAt = optString("expires_at").takeUnless { it.isEmpty() || it == "null" },
        )
    }

    private companion object {
        const val AES_KEY_SIZE = 256
        const val ANDROID_KEYSTORE = "AndroidKeyStore"
        const val GCM_TAG_LENGTH = 128
        const val KEY_ALIAS = "discrobble.integration.tokens"
        const val SHARED_PREFERENCES_NAME = "discrobble.secure.tokens"
        const val TRANSFORMATION = "AES/GCM/NoPadding"
    }

    private data class EncryptedPayload(
        val ciphertext: String,
        val iv: String,
    )
}
