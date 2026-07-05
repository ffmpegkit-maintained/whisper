package dev.ffmpegkit.whisper.sample

import android.os.Bundle
import android.util.Log
import android.widget.ScrollView
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import dev.ffmpegkit.whisper.Whisper
import dev.ffmpegkit.whisper.WhisperConfig
import kotlinx.coroutines.launch
import java.io.File

/**
 * Minimal demo: transcribe the bundled `jfk.wav` with a ggml model.
 *
 * The model is NOT bundled (too large). Download one (see README) and push it:
 *   adb push ggml-base.en.bin /sdcard/Android/data/dev.ffmpegkit.whisper.sample/files/models/
 */
class MainActivity : AppCompatActivity() {

    private val log = StringBuilder()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val view = TextView(this).apply {
            setPadding(32, 32, 32, 32)
            textSize = 13f
        }
        setContentView(ScrollView(this).apply { addView(view) })

        fun render(line: String) {
            log.append(line).append('\n')
            view.text = log
        }

        render("System info:")
        render(Whisper.getSystemInfo())
        render("")

        lifecycleScope.launch {
            try {
                // 1) Copy the bundled test WAV to a real file path.
                val wav = File(cacheDir, "jfk.wav")
                if (!wav.exists()) {
                    assets.open("samples/jfk.wav").use { input ->
                        wav.outputStream().use { input.copyTo(it) }
                    }
                }

                // 2) Locate the model pushed via adb.
                val model = File(getExternalFilesDir("models"), "ggml-base.en.bin")
                if (!model.exists()) {
                    render("No model at:\n${model.absolutePath}")
                    render("\nDownload a ggml model (see README → Model Download) and adb push it there.")
                    return@launch
                }

                render("Loading model…")
                val handle = Whisper.loadModel(this@MainActivity, model.absolutePath)

                render("Transcribing jfk.wav…")
                val result = Whisper.transcribe(
                    handle,
                    wav.absolutePath,
                    WhisperConfig(language = "en"),
                )

                render("\n=== Transcription (${result.processingTimeMs} ms) ===")
                render(result.text)
                result.segments.forEach { s ->
                    render("[${s.startMs}–${s.endMs} ms] ${s.text}")
                }

                Whisper.releaseModel(handle)
            } catch (e: Exception) {
                Log.e("WhisperSample", "transcription failed", e)
                render("\nError: ${e.message}")
            }
        }
    }
}
