package dev.ffmpegkit.whisper

/**
 * Transcription options.
 *
 * @param language        ISO 639-1 code (e.g. "en", "fr") or "auto" for detection.
 * @param translate       Translate the transcription to English.
 * @param threads         Number of CPU threads to use.
 * @param maxSegmentLength Max characters per segment (0 = no limit).
 * @param printTimestamps Include per-segment timestamps in the result.
 */
data class WhisperConfig(
    val language: String = "auto",
    val translate: Boolean = false,
    val threads: Int = 4,
    val maxSegmentLength: Int = 0,
    val printTimestamps: Boolean = true,
)
