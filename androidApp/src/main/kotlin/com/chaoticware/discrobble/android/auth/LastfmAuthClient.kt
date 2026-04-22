package com.chaoticware.discrobble.android.auth

import android.util.Base64
import com.chaoticware.discrobble.android.BuildConfig
import com.chaoticware.discrobble.android.security.StoredIntegrationTokenSet
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.nio.charset.StandardCharsets
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

class LastfmAuthClient(
    private val attemptStore: PendingAuthAttemptStore,
    private val workerBaseUrl: String = BuildConfig.DISCROBBLE_WORKER_BASE_URL,
) {
    fun startAuth(): String {
        val attempt = attemptStore.createAttempt(AuthProvider.LASTFM)

        return runCatching {
            val requestBody = JSONObject()
                .put("callback_url", CALLBACK_URL)
                .put("device_public_key", attempt.publicKeyPem)
                .put("platform", "android")

            val (statusCode, responseBody) = postJson(
                path = "/auth/lastfm/start",
                body = requestBody.toString(),
            )

            if (statusCode !in 200..299) {
                throw IllegalStateException(extractWorkerError(responseBody))
            }

            JSONObject(responseBody).optString("authorize_url").takeIf { it.isNotBlank() }
                ?: throw IllegalStateException("The Worker did not return a Last.fm authorize URL.")
        }.getOrElse { throwable ->
            attemptStore.removeAttempt(AuthProvider.LASTFM)
            throw throwable
        }
    }

    fun consumeCallback(callback: PendingAuthCallback): StoredIntegrationTokenSet {
        require(callback.provider == AuthProvider.LASTFM) {
            "Only Last.fm auth is implemented in this shell."
        }

        val attempt = attemptStore.loadAttempt(AuthProvider.LASTFM)
            ?: throw IllegalStateException(
                "No pending Last.fm auth attempt was available to decrypt the callback.",
            )
        val envelope = JSONObject(callback.encryptedPayload)

        require(envelope.optString("alg") == ENVELOPE_ALGORITHM) {
            "The Last.fm auth callback used an unsupported encryption envelope."
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
            info = HANDOFF_INFO.toByteArray(StandardCharsets.UTF_8),
            outputLength = 32,
        )
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")

        cipher.init(
            Cipher.DECRYPT_MODE,
            SecretKeySpec(aesKey, "AES"),
            GCMParameterSpec(128, iv),
        )

        val decryptedPayload = cipher.doFinal(ciphertext)
        val payload = JSONObject(String(decryptedPayload, StandardCharsets.UTF_8))
        val expiresAt = Instant.parse(payload.getString("expires_at"))

        require(expiresAt.isAfter(Instant.now())) {
            "The Last.fm auth callback expired before the app could decrypt it. Restart the auth flow."
        }

        attemptStore.removeAttempt(AuthProvider.LASTFM)

        return StoredIntegrationTokenSet(
            provider = AuthProvider.LASTFM,
            username = payload.getString("username"),
            accessToken = payload.getString("session_key"),
            accessSecret = null,
            issuedAt = payload.getString("issued_at"),
            expiresAt = null,
        )
    }

    fun clearAttempt(provider: AuthProvider) {
        attemptStore.removeAttempt(provider)
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

    private fun decodeEcPublicKey(pem: String): PublicKey {
        val normalized = pem
            .replace(PEM_PUBLIC_KEY_HEADER, "")
            .replace(PEM_PUBLIC_KEY_FOOTER, "")
            .replace("\\s+".toRegex(), "")
        val encodedKey = Base64.decode(normalized, Base64.DEFAULT)

        return KeyFactory.getInstance("EC").generatePublic(X509EncodedKeySpec(encodedKey))
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

    private fun extractWorkerError(responseBody: String): String {
        return runCatching {
            JSONObject(responseBody)
                .getJSONObject("error")
                .optString("message")
        }.getOrNull()?.takeIf { it.isNotBlank() }
            ?: "The Worker returned an unexpected Last.fm auth error."
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

    private fun postJson(
        path: String,
        body: String,
    ): Pair<Int, String> {
        val url = URL(buildUrl(path))
        val connection = url.openConnection() as HttpURLConnection

        return try {
            connection.requestMethod = "POST"
            connection.connectTimeout = 15_000
            connection.readTimeout = 15_000
            connection.doOutput = true
            connection.setRequestProperty("Content-Type", "application/json")
            connection.outputStream.use { output ->
                output.write(body.toByteArray(StandardCharsets.UTF_8))
            }

            val responseCode = connection.responseCode
            val responseBody = readResponseBody(connection, responseCode)
            responseCode to responseBody
        } finally {
            connection.disconnect()
        }
    }

    private fun buildUrl(path: String): String {
        val normalizedBaseUrl = workerBaseUrl.trimEnd('/')
        return "$normalizedBaseUrl$path"
    }

    private fun readResponseBody(
        connection: HttpURLConnection,
        statusCode: Int,
    ): String {
        val stream = if (statusCode in 200..299) {
            connection.inputStream
        } else {
            connection.errorStream
        } ?: return ""

        return stream.bufferedReader(StandardCharsets.UTF_8).use { reader ->
            reader.readText()
        }
    }

    private companion object {
        const val CALLBACK_URL = "discrobble://auth/lastfm"
        const val ENVELOPE_ALGORITHM = "ECDH-P256+HKDF-SHA256+A256GCM"
        const val HANDOFF_INFO = "discrobble-auth-handoff:v1"
        const val PEM_PUBLIC_KEY_FOOTER = "-----END PUBLIC KEY-----"
        const val PEM_PUBLIC_KEY_HEADER = "-----BEGIN PUBLIC KEY-----"
    }
}
