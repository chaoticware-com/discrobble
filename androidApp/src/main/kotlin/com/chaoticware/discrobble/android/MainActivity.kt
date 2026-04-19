package com.chaoticware.discrobble.android

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import com.chaoticware.discrobble.android.auth.AuthShellStateHolder
import com.chaoticware.discrobble.android.security.AndroidKeystoreTokenStore

class MainActivity : ComponentActivity() {
    private val authShellStateHolder: AuthShellStateHolder by lazy {
        AuthShellStateHolder(
            tokenStore = AndroidKeystoreTokenStore(applicationContext),
        )
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        authShellStateHolder.handleIncomingIntent(intent)

        setContent {
            DiscrobbleAndroidShell(
                stateHolder = authShellStateHolder,
            )
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        authShellStateHolder.handleIncomingIntent(intent)
    }
}
