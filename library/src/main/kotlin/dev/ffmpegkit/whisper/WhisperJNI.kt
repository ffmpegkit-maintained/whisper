package dev.ffmpegkit.whisper

/**
 * Internal bridge to the native library (libwhisper.so).
 * Not part of the public API — use [Whisper] instead.
 */
internal object WhisperJNI {

    init {
        System.loadLibrary("whisper")
    }

    /** @return native `whisper_context*` as a handle, or 0 on failure. */
    external fun nativeLoadModel(path: String): Long

    /** @return JSON: `{"text":"...","segments":[{"startMs":..,"endMs":..,"text":".."}]}`. */
    external fun nativeTranscribe(
        modelHandle: Long,
        audioPath: String,
        language: String,
        translate: Boolean,
        threads: Int,
    ): String

    external fun nativeReleaseModel(handle: Long)

    external fun nativeGetSystemInfo(): String
}
