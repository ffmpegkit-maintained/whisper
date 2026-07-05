# Whisper Android — Prebuilt on-device speech-to-text AAR

[![Maven Central](https://img.shields.io/maven-central/v/dev.ffmpegkit-maintained/whisper-android)](https://central.sonatype.com/artifact/dev.ffmpegkit-maintained/whisper-android)
[![JitPack](https://jitpack.io/v/ffmpegkit-maintained/whisper.svg)](https://jitpack.io/#ffmpegkit-maintained/whisper)
[![License: MIT](https://img.shields.io/badge/License-MIT-yellow.svg)](LICENSE)
[![Website](https://img.shields.io/badge/website-jokobee.com-blue.svg)](https://www.jokobee.com)

**Run [whisper.cpp](https://github.com/ggml-org/whisper.cpp) on Android with one Gradle line. No NDK, no source build.**

`whisper-android` is a prebuilt AAR that bundles whisper.cpp — a fast, on-device
speech-to-text engine — behind a clean Kotlin API. Everything runs **locally on
the device**: no network, no cloud, no API keys. 99 languages, optional
translation to English.

> You bring an audio file and a model file → you get text with timestamps.

---

## Install

Pick **one** of the two methods.

### A) Maven Central (recommended)

```kotlin
// build.gradle.kts (module)
dependencies {
    implementation("dev.ffmpegkit-maintained:whisper-android:0.1.0")
}
```

### B) JitPack

```kotlin
// settings.gradle.kts
dependencyResolutionManagement {
    repositories {
        google()
        mavenCentral()
        maven { url = uri("https://jitpack.io") } // add this
    }
}
```
```kotlin
// build.gradle.kts (module)
dependencies {
    implementation("com.github.ffmpegkit-maintained:whisper:v0.1.0")
}
```

### C) Direct AAR download

Grab `whisper-android-<version>.aar` from the [Releases](https://github.com/ffmpegkit-maintained/whisper/releases)
page, drop it in `app/libs/`, and add `implementation(files("libs/whisper-android-0.1.0.aar"))`.

---

## Quick Start

A complete, copy-paste example — even if you have never touched whisper.cpp or the NDK.

**1. Add the dependency** (see Install above).

**2. Download a model** (see [Model Download](#model-download)) and ship it, or push it during dev:

```
adb push ggml-base.en.bin /sdcard/Android/data/<your.app.id>/files/models/
```

**3. Transcribe:**

```kotlin
import androidx.lifecycle.lifecycleScope
import dev.ffmpegkit.whisper.Whisper
import dev.ffmpegkit.whisper.WhisperConfig
import kotlinx.coroutines.launch
import java.io.File

class MainActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        lifecycleScope.launch {
            // Model file (16 kHz mono 16-bit WAV audio is required).
            val modelPath = File(getExternalFilesDir("models"), "ggml-base.en.bin").absolutePath
            val audioPath = File(getExternalFilesDir(null), "speech.wav").absolutePath

            val model  = Whisper.loadModel(this@MainActivity, modelPath)
            val result = Whisper.transcribe(model, audioPath, WhisperConfig(language = "en"))

            Log.i("Whisper", "Text: ${result.text}")
            result.segments.forEach { s ->
                Log.i("Whisper", "[${s.startMs}–${s.endMs} ms] ${s.text}")
            }

            Whisper.releaseModel(model)
        }
    }
}
```

That's it. `Whisper.transcribe` is a `suspend` function — call it from a coroutine.

> **Audio format:** whisper.cpp needs **16 kHz, mono, 16-bit PCM WAV**. Resample
> your audio to that before calling `transcribe` (FFmpeg, `AudioRecord` at 16 kHz, etc.).

---

## Model Download

The model is **not** bundled in the AAR (models are 75 MB – 1.5 GB — far too big).
Download the one that fits your speed/quality/size budget from Hugging Face:

| Model | Size | Speed | Quality | Languages | Download |
|---|---|---|---|---|---|
| `tiny.en` | ~75 MB | ⚡⚡⚡ fastest | ★★ | English only | [ggml-tiny.en.bin](https://huggingface.co/ggml-org/whisper.cpp/resolve/main/ggml-tiny.en.bin) |
| `base` | ~142 MB | ⚡⚡ fast | ★★★ | 99 languages | [ggml-base.bin](https://huggingface.co/ggml-org/whisper.cpp/resolve/main/ggml-base.bin) |
| `base.en` | ~142 MB | ⚡⚡ fast | ★★★ | English only | [ggml-base.en.bin](https://huggingface.co/ggml-org/whisper.cpp/resolve/main/ggml-base.en.bin) |
| `small` | ~466 MB | ⚡ slower | ★★★★ | 99 languages | [ggml-small.bin](https://huggingface.co/ggml-org/whisper.cpp/resolve/main/ggml-small.bin) |

**Which one?** Start with **`base`** (or `base.en` for English-only) — the best
speed/quality trade-off on a phone. Use `tiny.en` if you need real-time-ish speed
on low-end devices, or `small` when accuracy matters more than latency.

Ship the model with your app (assets or a first-run download), then load it with
`Whisper.loadModel(context, path)` or `Whisper.loadModelFromAsset(context, "models/ggml-base.bin")`.

---

## Compatibility

| | |
|---|---|
| ABI | `arm64-v8a` (covers >90% of modern Android devices) |
| Android | API 24+ (Android 7.0 and up) |
| Android 15 | ✅ 16 KB page size aligned |
| NEON | ✅ enabled |
| compileSdk / targetSdk | 35 |

Need `x86_64` (emulators, Chromebooks), real-time streaming, VAD, or quantized
models? Those are in the **Pro** build — see [jokobee.com](https://www.jokobee.com).

---

## Documentation

Full guides on the **[Wiki](https://github.com/ffmpegkit-maintained/whisper/wiki)**:
Installation · Quick Start · Model Download · FAQ · Troubleshooting.

## API at a glance

```kotlin
object Whisper {
    suspend fun loadModel(context: Context, modelPath: String): WhisperModel
    suspend fun loadModelFromAsset(context: Context, assetName: String): WhisperModel
    suspend fun transcribe(model: WhisperModel, audioPath: String, config: WhisperConfig = WhisperConfig()): WhisperResult
    fun releaseModel(model: WhisperModel)
    fun getSystemInfo(): String
}
```

---

## Publisher

**Jokobee** · [https://www.jokobee.com](https://www.jokobee.com) · contact@jokobee.com
Maintained under the [`ffmpegkit-maintained`](https://github.com/ffmpegkit-maintained) organisation.

## License

MIT — see [LICENSE](LICENSE). whisper.cpp is also MIT (© Georgi Gerganov and contributors).
