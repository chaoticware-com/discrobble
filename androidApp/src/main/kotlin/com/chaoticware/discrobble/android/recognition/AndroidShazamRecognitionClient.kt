package com.chaoticware.discrobble.android.recognition

import android.annotation.SuppressLint
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import android.os.Process
import com.chaoticware.discrobble.android.BuildConfig
import java.lang.reflect.InvocationTargetException
import java.lang.reflect.Proxy
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlin.coroutines.intrinsics.COROUTINE_SUSPENDED
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext

enum class ShazamRecognitionPhase {
    PREPARING,
    RECORDING,
    MATCHING,
}

sealed interface AndroidShazamRecognitionAttemptResult {
    data class Match(val snapshot: ShazamRecognitionSnapshot) : AndroidShazamRecognitionAttemptResult
    data object NoMatch : AndroidShazamRecognitionAttemptResult
    data class Failure(val message: String) : AndroidShazamRecognitionAttemptResult
    data class Unavailable(val message: String) : AndroidShazamRecognitionAttemptResult
}

class AndroidShazamRecognitionClient(
    private val developerToken: String = BuildConfig.DISCROBBLE_SHAZAM_DEVELOPER_TOKEN,
    private val shazamKitAarPresent: Boolean = BuildConfig.DISCROBBLE_SHAZAMKIT_AAR_PRESENT,
) {
    @Volatile
    private var activeAudioRecord: AudioRecord? = null

    suspend fun recognizeFromMicrophone(
        onPhaseChanged: (ShazamRecognitionPhase) -> Unit,
    ): AndroidShazamRecognitionAttemptResult = withContext(Dispatchers.IO) {
        if (!shazamKitAarPresent) {
            return@withContext AndroidShazamRecognitionAttemptResult.Unavailable(
                "Android ShazamKit requires Apple's local libs/shazamkit-android-release.aar. Add the SDK to the repo-root libs directory and rebuild the app.",
            )
        }

        if (developerToken.isBlank()) {
            return@withContext AndroidShazamRecognitionAttemptResult.Unavailable(
                "Android ShazamKit requires a local discrobble.shazam.developerToken Gradle property before the spike can call Apple's catalog.",
            )
        }

        val shazamKit = loadShazamKitObject()
            ?: return@withContext AndroidShazamRecognitionAttemptResult.Unavailable(
                "The Android ShazamKit classes were not on the app classpath after build. Rebuild after adding Apple's local AAR.",
            )

        try {
            withContext(Dispatchers.Main.immediate) {
                onPhaseChanged(ShazamRecognitionPhase.PREPARING)
            }

            val catalog = createShazamCatalog(
                shazamKit = shazamKit,
                developerToken = developerToken,
            )
            val captureDurationMs = maximumQuerySignatureDurationInMs(catalog)

            withContext(Dispatchers.Main.immediate) {
                onPhaseChanged(ShazamRecognitionPhase.RECORDING)
            }
            val audioBytes = recordMicrophoneAudio(captureDurationMs)

            ensureActive()
            withContext(Dispatchers.Main.immediate) {
                onPhaseChanged(ShazamRecognitionPhase.MATCHING)
            }

            val signatureGenerator = unwrapShazamKitResult(
                invokeSuspend(
                    receiver = shazamKit,
                    methodName = "createSignatureGenerator",
                    args = arrayOf(loadAudioSampleRateConstant()),
                ),
            )
            invokeMethod(
                receiver = signatureGenerator,
                methodName = "append",
                args = arrayOf(audioBytes, audioBytes.size, System.currentTimeMillis()),
            )
            val signature = invokeMethod(
                receiver = signatureGenerator,
                methodName = "generateSignature",
            )
            val session = unwrapShazamKitResult(
                invokeSuspend(
                    receiver = shazamKit,
                    methodName = "createSession",
                    args = arrayOf(catalog),
                ),
            )
            val matchResult = invokeSuspend(
                receiver = session,
                methodName = "match",
                args = arrayOf(signature),
            )

            return@withContext parseMatchResult(matchResult)
        } catch (throwable: Throwable) {
            return@withContext AndroidShazamRecognitionAttemptResult.Failure(
                throwable.message ?: throwable.javaClass.simpleName,
            )
        } finally {
            cancelActiveAttempt()
        }
    }

    fun cancelActiveAttempt() {
        val audioRecord = activeAudioRecord ?: return
        activeAudioRecord = null

        runCatching {
            if (audioRecord.recordingState == AudioRecord.RECORDSTATE_RECORDING) {
                audioRecord.stop()
            }
        }
        runCatching {
            audioRecord.release()
        }
    }

    @SuppressLint("MissingPermission")
    private suspend fun recordMicrophoneAudio(durationMs: Int): ByteArray = withContext(Dispatchers.IO) {
        val bufferSize = AudioRecord.getMinBufferSize(
            SAMPLE_RATE_HZ,
            AudioFormat.CHANNEL_IN_MONO,
            AudioFormat.ENCODING_PCM_16BIT,
        ).coerceAtLeast(4_096)
        val targetSize = ((SAMPLE_RATE_HZ * BYTES_PER_SAMPLE) * (durationMs / 1_000f)).toInt()
        val destination = ByteArray(targetSize)
        val audioRecord = createAudioRecord(bufferSize)
        var bytesWritten = 0

        activeAudioRecord = audioRecord

        try {
            Process.setThreadPriority(Process.THREAD_PRIORITY_URGENT_AUDIO)
            audioRecord.startRecording()

            while (bytesWritten < destination.size) {
                ensureActive()
                val bytesRemaining = destination.size - bytesWritten
                val bytesRead = audioRecord.read(
                    destination,
                    bytesWritten,
                    minOf(bufferSize, bytesRemaining),
                )

                if (bytesRead <= 0) {
                    throw IllegalStateException(
                        "AudioRecord returned $bytesRead while capturing the Android ShazamKit spike sample.",
                    )
                }

                bytesWritten += bytesRead
            }
        } finally {
            cancelActiveAttempt()
        }

        return@withContext destination
    }

    private fun createAudioRecord(bufferSize: Int): AudioRecord {
        val audioFormat = AudioFormat.Builder()
            .setChannelMask(AudioFormat.CHANNEL_IN_MONO)
            .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
            .setSampleRate(SAMPLE_RATE_HZ)
            .build()

        val sources = listOf(
            MediaRecorder.AudioSource.UNPROCESSED,
            MediaRecorder.AudioSource.VOICE_RECOGNITION,
            MediaRecorder.AudioSource.MIC,
        )

        sources.forEach { source ->
            val audioRecord = runCatching {
                AudioRecord.Builder()
                    .setAudioSource(source)
                    .setAudioFormat(audioFormat)
                    .setBufferSizeInBytes(bufferSize)
                    .build()
            }.getOrNull() ?: return@forEach

            if (audioRecord.state == AudioRecord.STATE_INITIALIZED) {
                return audioRecord
            }

            audioRecord.release()
        }

        throw IllegalStateException("Failed to initialize an Android AudioRecord source for the ShazamKit spike.")
    }

    private fun loadShazamKitObject(): Any? {
        return runCatching {
            val shazamKitClass = Class.forName(CLASS_SHAZAM_KIT)
            shazamKitClass.getField("INSTANCE").get(null)
        }.getOrNull()
    }

    private fun createShazamCatalog(
        shazamKit: Any,
        developerToken: String,
    ): Any {
        val developerTokenClass = Class.forName(CLASS_DEVELOPER_TOKEN)
        val providerClass = Class.forName(CLASS_DEVELOPER_TOKEN_PROVIDER)
        val developerTokenInstance = developerTokenClass
            .getConstructor(String::class.java)
            .newInstance(developerToken)

        val provider = Proxy.newProxyInstance(
            providerClass.classLoader,
            arrayOf(providerClass),
        ) { _, method, _ ->
            when (method.name) {
                "provideDeveloperToken" -> developerTokenInstance
                "toString" -> "DiscrobbleAndroidShazamDeveloperTokenProvider"
                "hashCode" -> System.identityHashCode(developerTokenInstance)
                "equals" -> false
                else -> throw UnsupportedOperationException("Unsupported DeveloperTokenProvider method ${method.name}.")
            }
        }

        return invokeMethod(
            receiver = shazamKit,
            methodName = "createShazamCatalog",
            args = arrayOf(provider, null),
        )
    }

    private fun loadAudioSampleRateConstant(): Any {
        val enumClass = Class.forName(CLASS_AUDIO_SAMPLE_RATE)
        return enumClass.enumConstants.orEmpty().first { constant ->
            (constant as Enum<*>).name == AUDIO_SAMPLE_RATE_CONSTANT
        }
    }

    private fun maximumQuerySignatureDurationInMs(catalog: Any): Int {
        val duration = invokeGetterOrNull(catalog, "getMaximumQuerySignatureDurationInMs") as? Number
        return duration?.toInt() ?: DEFAULT_CAPTURE_DURATION_MS
    }

    private fun parseMatchResult(matchResult: Any?): AndroidShazamRecognitionAttemptResult {
        requireNotNull(matchResult) {
            "Android ShazamKit returned a null match result."
        }

        return when (matchResult.javaClass.simpleName) {
            "Match" -> {
                val matchedMediaItems = invokeMethod(
                    receiver = matchResult,
                    methodName = "getMatchedMediaItems",
                ) as? List<*> ?: emptyList<Any>()

                AndroidShazamRecognitionAttemptResult.Match(
                    snapshot = ShazamRecognitionSnapshot(
                        candidates = matchedMediaItems.mapIndexedNotNull { index, item ->
                            item?.let { toCandidate(index, it) }
                        },
                        capturedAtMillis = System.currentTimeMillis(),
                    ),
                )
            }

            "NoMatch" -> AndroidShazamRecognitionAttemptResult.NoMatch
            "Error" -> {
                val exception = invokeMethod(
                    receiver = matchResult,
                    methodName = "getException",
                ) as? Throwable

                AndroidShazamRecognitionAttemptResult.Failure(
                    exception?.message ?: "Android ShazamKit returned a match error.",
                )
            }

            else -> AndroidShazamRecognitionAttemptResult.Failure(
                "Android ShazamKit returned an unsupported result type: ${matchResult.javaClass.name}",
            )
        }
    }

    private fun toCandidate(index: Int, item: Any): ShazamRecognitionCandidate {
        return ShazamRecognitionCandidate(
            rank = index + 1,
            title = (invokeGetterOrNull(item, "getTitle") as? String).orEmpty().ifBlank { "Unknown title" },
            subtitle = invokeGetterOrNull(item, "getSubtitle") as? String,
            artist = invokeGetterOrNull(item, "getArtist") as? String,
            appleMusicId = invokeGetterOrNull(item, "getAppleMusicID") as? String,
            appleMusicUrl = invokeGetterOrNull(item, "getAppleMusicURL")?.toString(),
            explicitContent = invokeGetterOrNull(item, "getExplicitContent") as? Boolean,
            frequencySkew = (invokeGetterOrNull(item, "getFrequencySkew") as? Number)?.toFloat(),
            frequencySkewRangeCount = (invokeGetterOrNull(item, "getFrequencySkewRanges") as? List<*>)?.size ?: 0,
            genres = (invokeGetterOrNull(item, "getGenres") as? List<*>)?.mapNotNull { it as? String }.orEmpty(),
            isrc = invokeGetterOrNull(item, "getIsrc") as? String,
            matchOffsetInMs = (invokeGetterOrNull(item, "getMatchOffsetInMs") as? Number)?.toFloat(),
            predictedCurrentMatchOffsetInMs = (invokeGetterOrNull(item, "getPredictedCurrentMatchOffset") as? Number)?.toFloat(),
            shazamId = invokeGetterOrNull(item, "getShazamID") as? String,
            timeRangeCount = (invokeGetterOrNull(item, "getTimeRanges") as? List<*>)?.size ?: 0,
            webUrl = invokeGetterOrNull(item, "getWebURL")?.toString(),
        )
    }

    private fun unwrapShazamKitResult(result: Any?): Any {
        requireNotNull(result) {
            "Android ShazamKit returned a null ShazamKitResult."
        }

        return when (result.javaClass.simpleName) {
            "Success" -> invokeMethod(
                receiver = result,
                methodName = "getData",
            )

            "Failure" -> {
                val reason = invokeMethod(
                    receiver = result,
                    methodName = "getReason",
                ) as? Throwable

                throw IllegalStateException(
                    reason?.message ?: "Android ShazamKit returned a failure result.",
                    reason,
                )
            }

            else -> throw IllegalStateException("Unsupported ShazamKitResult type ${result.javaClass.name}.")
        }
    }

    private fun invokeMethod(
        receiver: Any,
        methodName: String,
        args: Array<out Any?> = emptyArray(),
    ): Any {
        val method = receiver.javaClass.methods.firstOrNull { candidate ->
            candidate.name == methodName && candidate.parameterCount == args.size
        } ?: throw NoSuchMethodException("${receiver.javaClass.name}#$methodName/${args.size}")

        return try {
            method.invoke(receiver, *args)
        } catch (throwable: InvocationTargetException) {
            throw throwable.targetException ?: throwable
        }
    }

    private fun invokeGetterOrNull(receiver: Any, methodName: String): Any? {
        return runCatching {
            invokeMethod(receiver, methodName)
        }.getOrNull()
    }

    private suspend fun invokeSuspend(
        receiver: Any,
        methodName: String,
        args: Array<out Any?> = emptyArray(),
    ): Any? = suspendCancellableCoroutine { continuation ->
        val method = receiver.javaClass.methods.firstOrNull { candidate ->
            candidate.name == methodName && candidate.parameterCount == args.size + 1
        } ?: run {
            continuation.resumeWithException(
                NoSuchMethodException("${receiver.javaClass.name}#$methodName/${args.size + 1}"),
            )
            return@suspendCancellableCoroutine
        }

        try {
            val result = method.invoke(receiver, *args, continuation)
            if (result !== COROUTINE_SUSPENDED) {
                continuation.resume(result)
            }
        } catch (throwable: InvocationTargetException) {
            continuation.resumeWithException(throwable.targetException ?: throwable)
        } catch (throwable: Throwable) {
            continuation.resumeWithException(throwable)
        }
    }

    companion object {
        const val RESULT_SHAPE_SUMMARY =
            "Current Android spike path: record one-shot PCM audio, call Session.match(signature), and normalize MatchResult.Match.matchedMediaItems. Each documented candidate exposes title, subtitle, artist, genres, explicit flag, ISRC, Shazam and Apple Music IDs, URLs, match offsets, frequency skew, and range metadata."

        private const val AUDIO_SAMPLE_RATE_CONSTANT = "SAMPLE_RATE_48000"
        private const val BYTES_PER_SAMPLE = 2
        private const val CLASS_AUDIO_SAMPLE_RATE = "com.shazam.shazamkit.AudioSampleRateInHz"
        private const val CLASS_DEVELOPER_TOKEN = "com.shazam.shazamkit.DeveloperToken"
        private const val CLASS_DEVELOPER_TOKEN_PROVIDER = "com.shazam.shazamkit.DeveloperTokenProvider"
        private const val CLASS_SHAZAM_KIT = "com.shazam.shazamkit.ShazamKit"
        private const val DEFAULT_CAPTURE_DURATION_MS = 12_000
        private const val SAMPLE_RATE_HZ = 48_000
    }
}
