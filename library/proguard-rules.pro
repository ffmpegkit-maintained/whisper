# The JNI bridge is resolved by name from native code — keep it intact.
-keep class dev.ffmpegkit.whisper.WhisperJNI { *; }

# Keep all native method signatures.
-keepclasseswithmembernames class * {
    native <methods>;
}

# Public API surface — keep names for consumers.
-keep public class dev.ffmpegkit.whisper.Whisper { public *; }
-keep public class dev.ffmpegkit.whisper.WhisperModel { public *; }
-keep public class dev.ffmpegkit.whisper.WhisperConfig { public *; }
-keep public class dev.ffmpegkit.whisper.WhisperResult { public *; }
-keep public class dev.ffmpegkit.whisper.WhisperSegment { public *; }
-keep public class dev.ffmpegkit.whisper.WhisperException { public *; }
