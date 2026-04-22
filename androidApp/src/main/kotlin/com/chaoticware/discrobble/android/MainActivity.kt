package com.chaoticware.discrobble.android

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import com.chaoticware.discrobble.android.auth.AuthShellStateHolder
import com.chaoticware.discrobble.android.auth.DiscogsAuthClient
import com.chaoticware.discrobble.android.auth.EncryptedPendingAuthAttemptStore
import com.chaoticware.discrobble.android.auth.LastfmAuthClient
import com.chaoticware.discrobble.android.recognition.ShazamRecognitionStateHolder
import com.chaoticware.discrobble.android.security.AndroidKeystoreTokenStore

class MainActivity : ComponentActivity() {
    private val authShellStateHolder: AuthShellStateHolder by lazy {
        val pendingAuthAttemptStore = EncryptedPendingAuthAttemptStore(applicationContext)
        AuthShellStateHolder(
            discogsAuthClient = DiscogsAuthClient(pendingAuthAttemptStore),
            lastfmAuthClient = LastfmAuthClient(pendingAuthAttemptStore),
            tokenStore = AndroidKeystoreTokenStore(applicationContext),
        )
    }

    private val shazamRecognitionStateHolder: ShazamRecognitionStateHolder by lazy {
        ShazamRecognitionStateHolder(applicationContext)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        authShellStateHolder.handleIncomingIntent(intent)

        setContent {
            DiscrobbleAndroidShell(
                authStateHolder = authShellStateHolder,
                shazamStateHolder = shazamRecognitionStateHolder,
            )
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        authShellStateHolder.handleIncomingIntent(intent)
    }

    override fun onDestroy() {
        shazamRecognitionStateHolder.dispose()
        if (!isChangingConfigurations) {
            authShellStateHolder.dispose()
        }
        super.onDestroy()
    }
}
