package dev.ffmpegkit.whisper

/**
 * Result of a transcription.
 *
 * @param text             Full transcription text.
 * @param segments         Time-stamped segments.
 * @param processingTimeMs Wall-clock time spent transcribing, in milliseconds.
 */
data class WhisperResult(
    val text: String,
    val segments: List<WhisperSegment>,
    val processingTimeMs: Long,
)

/** A single time-stamped segment of the transcription. */
data class WhisperSegment(
    val startMs: Long,
    val endMs: Long,
    val text: String,
)
