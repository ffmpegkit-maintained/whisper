package dev.ffmpegkit.whisper

/** Typed exceptions surfaced by the whisper-android API. */
sealed class WhisperException(message: String, cause: Throwable? = null) : Exception(message, cause) {

    /** The ggml model file could not be found or initialised. */
    class ModelLoadException(message: String, cause: Throwable? = null) : WhisperException(message, cause)

    /** whisper.cpp failed while transcribing. */
    class TranscriptionException(message: String, cause: Throwable? = null) : WhisperException(message, cause)

    /** The audio input is missing or in an unsupported format. */
    class InvalidAudioException(message: String, cause: Throwable? = null) : WhisperException(message, cause)
}
