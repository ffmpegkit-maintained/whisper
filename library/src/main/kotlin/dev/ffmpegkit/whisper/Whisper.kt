package dev.ffmpegkit.whisper

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.File

/**
 * Entry point for on-device speech-to-text with whisper.cpp.
 *
 * Typical use:
 * ```
 * val model = Whisper.loadModelFromAsset(context, "models/ggml-base.bin")
 * val result = Whisper.transcribe(model, wavPath, WhisperConfig(language = "en"))
 * println(result.text)
 * Whisper.releaseModel(model)
 * ```
 *
 * Audio must be 16-bit PCM WAV, 16 kHz, mono. Resample beforehand if needed.
 */
object Whisper {

    /** Load a ggml model from an absolute filesystem path. */
    suspend fun loadModel(context: Context, modelPath: String): WhisperModel =
        withContext(Dispatchers.IO) {
            val file = File(modelPath)
            if (!file.exists()) {
                throw WhisperException.ModelLoadException("Model not found: $modelPath")
            }
            val handle = WhisperJNI.nativeLoadModel(file.absolutePath)
            if (handle == 0L) {
                throw WhisperException.ModelLoadException("Failed to initialise model: $modelPath")
            }
            WhisperModel(handle, file.absolutePath)
        }

    /**
     * Load a ggml model bundled in the app's assets. The asset is copied to the
     * cache directory first, because the native loader needs a real file path.
     */
    suspend fun loadModelFromAsset(context: Context, assetName: String): WhisperModel =
        withContext(Dispatchers.IO) {
            val out = File(context.cacheDir, assetName.substringAfterLast('/'))
            if (!out.exists()) {
                context.assets.open(assetName).use { input ->
                    out.outputStream().use { input.copyTo(it) }
                }
            }
            loadModel(context, out.absolutePath)
        }

    /**
     * Transcribe an audio file.
     *
     * @param audioPath 16-bit PCM WAV, 16 kHz, mono.
     */
    suspend fun transcribe(
        model: WhisperModel,
        audioPath: String,
        config: WhisperConfig = WhisperConfig(),
    ): WhisperResult = withContext(Dispatchers.Default) {
        if (!model.isValid) {
            throw WhisperException.TranscriptionException("Model has been released or is invalid")
        }
        val audio = File(audioPath)
        if (!audio.exists()) {
            throw WhisperException.InvalidAudioException("Audio not found: $audioPath")
        }

        val start = System.currentTimeMillis()
        val json = WhisperJNI.nativeTranscribe(
            model.nativeHandle,
            audio.absolutePath,
            config.language,
            config.translate,
            config.threads,
        )
        val elapsed = System.currentTimeMillis() - start
        parseResult(json, elapsed)
    }

    /** Free the native context. Safe to call more than once. */
    fun releaseModel(model: WhisperModel) {
        if (model.isValid) {
            WhisperJNI.nativeReleaseModel(model.nativeHandle)
            model.released = true
        }
    }

    /** Native build/runtime info (NEON, BLAS, threads, …). */
    fun getSystemInfo(): String = WhisperJNI.nativeGetSystemInfo()

    private fun parseResult(json: String, elapsedMs: Long): WhisperResult {
        val obj = JSONObject(json)
        if (obj.has("error")) {
            throw WhisperException.TranscriptionException(obj.getString("error"))
        }
        val segsJson = obj.optJSONArray("segments")
        val segments = buildList {
            if (segsJson != null) {
                for (i in 0 until segsJson.length()) {
                    val s = segsJson.getJSONObject(i)
                    add(
                        WhisperSegment(
                            startMs = s.getLong("startMs"),
                            endMs = s.getLong("endMs"),
                            text = s.getString("text").trim(),
                        ),
                    )
                }
            }
        }
        return WhisperResult(
            text = obj.getString("text").trim(),
            segments = segments,
            processingTimeMs = elapsedMs,
        )
    }
}
