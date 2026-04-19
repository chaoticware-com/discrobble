package com.chaoticware.discrobble.android.recognition

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.core.content.ContextCompat
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

enum class ShazamMicrophonePermissionStatus(val displayName: String) {
    GRANTED("Granted"),
    REQUIRED("Required"),
}

sealed interface ShazamRecognitionState {
    val isActive: Boolean
    val statusLine: String

    data object Idle : ShazamRecognitionState {
        override val isActive: Boolean = false
        override val statusLine: String = "Ready to capture one Android ShazamKit match attempt."
    }

    data object Preparing : ShazamRecognitionState {
        override val isActive: Boolean = true
        override val statusLine: String = "Preparing the Android ShazamKit spike."
    }

    data object Recording : ShazamRecognitionState {
        override val isActive: Boolean = true
        override val statusLine: String = "Recording a one-shot PCM sample from the microphone."
    }

    data object Matching : ShazamRecognitionState {
        override val isActive: Boolean = true
        override val statusLine: String = "Generating a signature and matching it against ShazamKit."
    }

    data object Matched : ShazamRecognitionState {
        override val isActive: Boolean = false
        override val statusLine: String = "Received a ranked Android ShazamKit match result."
    }

    data object NoMatch : ShazamRecognitionState {
        override val isActive: Boolean = false
        override val statusLine: String = "Android ShazamKit completed without a match."
    }

    data class Failed(private val message: String) : ShazamRecognitionState {
        override val isActive: Boolean = false
        override val statusLine: String = message
    }

    data class Unavailable(private val message: String) : ShazamRecognitionState {
        override val isActive: Boolean = false
        override val statusLine: String = message
    }
}

data class ShazamRecognitionCandidate(
    val rank: Int,
    val title: String,
    val subtitle: String?,
    val artist: String?,
    val appleMusicId: String?,
    val appleMusicUrl: String?,
    val explicitContent: Boolean?,
    val frequencySkew: Float?,
    val frequencySkewRangeCount: Int,
    val genres: List<String>,
    val isrc: String?,
    val matchOffsetInMs: Float?,
    val predictedCurrentMatchOffsetInMs: Float?,
    val shazamId: String?,
    val timeRangeCount: Int,
    val webUrl: String?,
)

data class ShazamRecognitionSnapshot(
    val candidates: List<ShazamRecognitionCandidate>,
    val capturedAtMillis: Long,
)

class ShazamRecognitionStateHolder(
    private val appContext: Context,
    private val recognitionClient: AndroidShazamRecognitionClient = AndroidShazamRecognitionClient(),
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private var recognitionJob: Job? = null

    var lastMatch by mutableStateOf<ShazamRecognitionSnapshot?>(null)
        private set

    var lastNoMatchAtMillis by mutableStateOf<Long?>(null)
        private set

    var permissionStatus by mutableStateOf(currentPermissionStatus())
        private set

    var recognitionState by mutableStateOf<ShazamRecognitionState>(ShazamRecognitionState.Idle)
        private set

    val resultShapeSummary: String
        get() = AndroidShazamRecognitionClient.RESULT_SHAPE_SUMMARY

    fun clearLastMatch() {
        lastMatch = null
        lastNoMatchAtMillis = null
    }

    fun refreshPermissionStatus() {
        permissionStatus = currentPermissionStatus()
    }

    fun startRecognition() {
        refreshPermissionStatus()

        if (permissionStatus != ShazamMicrophonePermissionStatus.GRANTED) {
            recognitionState = ShazamRecognitionState.Failed(
                "Microphone permission is required before the Android ShazamKit spike can listen.",
            )
            return
        }

        cancelRecognition(resetToIdle = false)

        recognitionJob = scope.launch {
            recognitionState = ShazamRecognitionState.Preparing

            when (val result = recognitionClient.recognizeFromMicrophone(this@ShazamRecognitionStateHolder::setPhase)) {
                is AndroidShazamRecognitionAttemptResult.Match -> {
                    lastMatch = result.snapshot
                    lastNoMatchAtMillis = null
                    recognitionState = ShazamRecognitionState.Matched
                }

                is AndroidShazamRecognitionAttemptResult.NoMatch -> {
                    lastNoMatchAtMillis = System.currentTimeMillis()
                    recognitionState = ShazamRecognitionState.NoMatch
                }

                is AndroidShazamRecognitionAttemptResult.Failure -> {
                    recognitionState = ShazamRecognitionState.Failed(result.message)
                }

                is AndroidShazamRecognitionAttemptResult.Unavailable -> {
                    recognitionState = ShazamRecognitionState.Unavailable(result.message)
                }
            }
        }
    }

    fun onMicrophonePermissionResult(
        granted: Boolean,
        startRecognitionWhenGranted: Boolean,
    ) {
        permissionStatus = currentPermissionStatus()

        if (!granted) {
            recognitionState = ShazamRecognitionState.Failed(
                "Microphone permission is required before the Android ShazamKit spike can listen.",
            )
            return
        }

        if (startRecognitionWhenGranted) {
            startRecognition()
        }
    }

    fun cancelRecognition(resetToIdle: Boolean = true) {
        recognitionJob?.cancel()
        recognitionJob = null
        recognitionClient.cancelActiveAttempt()

        if (resetToIdle) {
            recognitionState = ShazamRecognitionState.Idle
        }
    }

    fun dispose() {
        cancelRecognition()
        scope.cancel()
    }

    private fun setPhase(phase: ShazamRecognitionPhase) {
        recognitionState = when (phase) {
            ShazamRecognitionPhase.PREPARING -> ShazamRecognitionState.Preparing
            ShazamRecognitionPhase.RECORDING -> ShazamRecognitionState.Recording
            ShazamRecognitionPhase.MATCHING -> ShazamRecognitionState.Matching
        }
    }

    private fun currentPermissionStatus(): ShazamMicrophonePermissionStatus {
        return if (
            ContextCompat.checkSelfPermission(
                appContext,
                Manifest.permission.RECORD_AUDIO,
            ) == PackageManager.PERMISSION_GRANTED
        ) {
            ShazamMicrophonePermissionStatus.GRANTED
        } else {
            ShazamMicrophonePermissionStatus.REQUIRED
        }
    }
}
