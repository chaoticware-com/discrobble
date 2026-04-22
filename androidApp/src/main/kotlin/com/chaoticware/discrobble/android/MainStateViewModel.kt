package com.chaoticware.discrobble.android

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import com.chaoticware.discrobble.android.auth.AuthShellStateHolder
import com.chaoticware.discrobble.android.auth.DiscogsAuthClient
import com.chaoticware.discrobble.android.auth.EncryptedPendingAuthAttemptStore
import com.chaoticware.discrobble.android.auth.LastfmAuthClient
import com.chaoticware.discrobble.android.recognition.ShazamRecognitionStateHolder
import com.chaoticware.discrobble.android.security.AndroidKeystoreTokenStore

class MainStateViewModel(
    application: Application,
) : AndroidViewModel(application) {
    private val pendingAuthAttemptStore = EncryptedPendingAuthAttemptStore(application)

    val authShellStateHolder = AuthShellStateHolder(
        discogsAuthClient = DiscogsAuthClient(pendingAuthAttemptStore),
        lastfmAuthClient = LastfmAuthClient(pendingAuthAttemptStore),
        tokenStore = AndroidKeystoreTokenStore(application),
    )

    val shazamRecognitionStateHolder = ShazamRecognitionStateHolder(application)

    override fun onCleared() {
        shazamRecognitionStateHolder.dispose()
        authShellStateHolder.dispose()
        super.onCleared()
    }
}
