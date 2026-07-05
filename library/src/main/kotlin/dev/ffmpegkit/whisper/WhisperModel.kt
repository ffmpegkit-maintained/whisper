package dev.ffmpegkit.whisper

/**
 * A loaded ggml model. Wraps the native `whisper_context` pointer.
 *
 * Obtain an instance via [Whisper.loadModel] / [Whisper.loadModelFromAsset],
 * and free it with [Whisper.releaseModel] when done.
 */
class WhisperModel internal constructor(
    internal val nativeHandle: Long,
    /** Absolute filesystem path the model was loaded from. */
    val path: String,
) {
    @Volatile
    internal var released: Boolean = false

    /** True while the native context is loaded and usable. */
    val isValid: Boolean
        get() = nativeHandle != 0L && !released
}
