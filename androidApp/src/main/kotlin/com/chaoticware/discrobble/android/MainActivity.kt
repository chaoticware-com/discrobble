package com.chaoticware.discrobble.android

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.viewModels

class MainActivity : ComponentActivity() {
    private val stateViewModel: MainStateViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        stateViewModel.authShellStateHolder.handleIncomingIntent(intent)

        setContent {
            DiscrobbleAndroidShell(
                authStateHolder = stateViewModel.authShellStateHolder,
                shazamStateHolder = stateViewModel.shazamRecognitionStateHolder,
            )
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        stateViewModel.authShellStateHolder.handleIncomingIntent(intent)
    }
}
