package com.chaoticware.discrobble.android.auth

import android.net.Uri
import android.util.Base64
import com.chaoticware.discrobble.android.BuildConfig
import com.chaoticware.discrobble.android.security.StoredIntegrationTokenSet
import org.json.JSONObject
import java.security.KeyFactory
import java.security.PublicKey
import java.security.spec.PKCS8EncodedKeySpec
import java.security.spec.X509EncodedKeySpec
import java.time.Instant
import javax.crypto.Cipher
import javax.crypto.KeyAgreement
import javax.crypto.Mac
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec

class DiscogsAuthClient(
    private val attemptStore: PendingAuthAttemptStore,
    private val workerBaseUrl: String = BuildConfig.DISCROBBLE_WORKER_BASE_URL,
) {
    fun startAuth(): String {
        val attempt = attemptStore.createAttempt(AuthProvider.DISCOGS)

        return runCatching {
            Uri.parse(buildUrl("/auth/discogs/start"))
                .buildUpon()
                .appendQueryParameter("callback_url", CALLBACK_URL)
                .appendQueryParameter("device_public_key", attempt.publicKeyPem)
                .appendQueryParameter("platform", "android")
                .build()
                .toString()
        }.getOrElse { throwable ->
            attemptStore.removeAttempt(AuthProvider.DISCOGS)
            throw throwable
        }
    }

    fun consumeCallback(callback: PendingAuthCallback): StoredIntegrationTokenSet {
        require(callback.provider == AuthProvider.DISCOGS) {
            "Only Discogs auth callbacks can be decrypted by this shell."
        }

        val attempt = attemptStore.loadAttempt(AuthProvider.DISCOGS)
            ?: throw IllegalStateException(
                "No pending Discogs auth attempt was available to decrypt the callback.",
            )
        val envelope = JSONObject(callback.encryptedPayload)

        require(envelope.optString("alg") == ENVELOPE_ALGORITHM) {
            "The Discogs auth callback used an unsupported encryption envelope."
        }

        val privateKeyBytes = Base64.decode(attempt.privateKeyPkcs8, Base64.NO_WRAP)
        val privateKey = KeyFactory.getInstance("EC")
            .generatePrivate(PKCS8EncodedKeySpec(privateKeyBytes))
        val publicKey = decodeEcPublicKey(envelope.getString("epk"))
        val sharedSecret = deriveSharedSecret(privateKey, publicKey)
        val salt = decodeBase64Url(envelope.getString("salt"))
        val iv = decodeBase64Url(envelope.getString("iv"))
        val ciphertext = decodeBase64Url(envelope.getString("ciphertext"))
        val aesKey = hkdfSha256(
            inputKeyMaterial = sharedSecret,
            salt = salt,
            info = HANDOFF_INFO.toByteArray(),
            outputLength = 32,
        )
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")

        cipher.init(
            Cipher.DECRYPT_MODE,
            SecretKeySpec(aesKey, "AES"),
            GCMParameterSpec(128, iv),
        )

        val decryptedPayload = cipher.doFinal(ciphertext)
        val payload = JSONObject(String(decryptedPayload))
        val expiresAt = Instant.parse(payload.getString("expires_at"))

        require(expiresAt.isAfter(Instant.now())) {
            "The Discogs auth callback expired before the app could decrypt it. Restart the auth flow."
        }

        attemptStore.removeAttempt(AuthProvider.DISCOGS)

        return StoredIntegrationTokenSet(
            provider = AuthProvider.DISCOGS,
            username = payload.getString("username"),
            accessToken = payload.getString("oauth_token"),
            accessSecret = payload.getString("oauth_token_secret"),
            issuedAt = payload.getString("issued_at"),
            expiresAt = null,
        )
    }

    fun clearAttempt(provider: AuthProvider) {
        attemptStore.removeAttempt(provider)
    }

    private fun buildUrl(path: String): String {
        return "${workerBaseUrl.trimEnd('/')}$path"
    }

    private fun decodeBase64Url(value: String): ByteArray {
        val padding = (4 - (value.length % 4)) % 4
        val normalized = buildString(value.length + padding) {
            append(value)
            repeat(padding) {
                append('=')
            }
        }

        return Base64.decode(
            normalized,
            Base64.URL_SAFE or Base64.NO_WRAP,
        )
    }

    private fun decodeEcPublicKey(pem: String): PublicKey {
        val normalized = pem
            .replace(PEM_PUBLIC_KEY_HEADER, "")
            .replace(PEM_PUBLIC_KEY_FOOTER, "")
            .replace("\\s+".toRegex(), "")
        val encodedKey = Base64.decode(normalized, Base64.DEFAULT)

        return KeyFactory.getInstance("EC").generatePublic(X509EncodedKeySpec(encodedKey))
    }

    private fun deriveSharedSecret(
        privateKey: java.security.PrivateKey,
        publicKey: PublicKey,
    ): ByteArray {
        val keyAgreement = KeyAgreement.getInstance("ECDH")
        keyAgreement.init(privateKey)
        keyAgreement.doPhase(publicKey, true)
        return keyAgreement.generateSecret()
    }

    private fun hkdfSha256(
        inputKeyMaterial: ByteArray,
        salt: ByteArray,
        info: ByteArray,
        outputLength: Int,
    ): ByteArray {
        val hmac = Mac.getInstance("HmacSHA256")
        val saltBytes = if (salt.isEmpty()) ByteArray(32) else salt

        hmac.init(SecretKeySpec(saltBytes, "HmacSHA256"))

        val pseudoRandomKey = hmac.doFinal(inputKeyMaterial)
        val blocks = mutableListOf<Byte>()
        var previous = ByteArray(0)
        var counter = 1

        while (blocks.size < outputLength) {
            hmac.init(SecretKeySpec(pseudoRandomKey, "HmacSHA256"))
            hmac.update(previous)
            hmac.update(info)
            hmac.update(counter.toByte())
            previous = hmac.doFinal()
            previous.forEach(blocks::add)
            counter += 1
        }

        return blocks.take(outputLength).toByteArray()
    }

    private companion object {
        const val CALLBACK_URL = "discrobble://auth/discogs"
        const val ENVELOPE_ALGORITHM = "ECDH-P256+HKDF-SHA256+A256GCM"
        const val HANDOFF_INFO = "discrobble-auth-handoff:v1"
        const val PEM_PUBLIC_KEY_FOOTER = "-----END PUBLIC KEY-----"
        const val PEM_PUBLIC_KEY_HEADER = "-----BEGIN PUBLIC KEY-----"
    }
}
