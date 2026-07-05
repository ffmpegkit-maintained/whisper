// JNI bridge between the Kotlin API (dev.ffmpegkit.whisper.WhisperJNI) and
// whisper.cpp. Produces libwhisper.so. Kotlin loads it via
// System.loadLibrary("whisper").
//
// Native entry points (must match WhisperJNI.kt exactly):
//   nativeLoadModel(path)                         -> jlong  (whisper_context*)
//   nativeTranscribe(handle, audio, lang, tr, n)  -> jstring (JSON)
//   nativeReleaseModel(handle)                    -> void
//   nativeGetSystemInfo()                         -> jstring

#include <jni.h>
#include <android/log.h>
#include <cstdint>
#include <cstdio>
#include <cstring>
#include <string>
#include <vector>

#include "whisper.h"

#define LOG_TAG "whisper-jni"
#define LOGI(...) __android_log_print(ANDROID_LOG_INFO, LOG_TAG, __VA_ARGS__)
#define LOGE(...) __android_log_print(ANDROID_LOG_ERROR, LOG_TAG, __VA_ARGS__)

namespace {

// Minimal 16-bit PCM WAV reader → mono float32 @ 16 kHz expected.
// whisper.cpp requires 16 kHz mono f32; callers should resample beforehand.
bool read_wav_pcm16(const char *path, std::vector<float> &pcmf32) {
    FILE *f = fopen(path, "rb");
    if (!f) return false;

    uint8_t header[44];
    if (fread(header, 1, 44, f) != 44) { fclose(f); return false; }

    // Bits 22-23: num channels; 34-35: bits per sample.
    const uint16_t channels = header[22] | (header[23] << 8);
    const uint16_t bits     = header[34] | (header[35] << 8);
    if (bits != 16) { fclose(f); return false; }

    std::vector<int16_t> raw;
    int16_t sample;
    while (fread(&sample, sizeof(int16_t), 1, f) == 1) raw.push_back(sample);
    fclose(f);

    const size_t frames = channels > 0 ? raw.size() / channels : 0;
    pcmf32.resize(frames);
    for (size_t i = 0; i < frames; ++i) {
        // Downmix to mono if needed.
        int32_t acc = 0;
        for (uint16_t c = 0; c < channels; ++c) acc += raw[i * channels + c];
        pcmf32[i] = static_cast<float>(acc) / (channels * 32768.0f);
    }
    return true;
}

std::string jstr(JNIEnv *env, jstring s) {
    if (!s) return {};
    const char *c = env->GetStringUTFChars(s, nullptr);
    std::string out = c ? c : "";
    if (c) env->ReleaseStringUTFChars(s, c);
    return out;
}

// Escape a UTF-8 string for embedding in JSON.
std::string json_escape(const std::string &in) {
    std::string out;
    out.reserve(in.size() + 8);
    for (char c : in) {
        switch (c) {
            case '"':  out += "\\\""; break;
            case '\\': out += "\\\\"; break;
            case '\n': out += "\\n";  break;
            case '\r': out += "\\r";  break;
            case '\t': out += "\\t";  break;
            default:   out += c;      break;
        }
    }
    return out;
}

} // namespace

extern "C" {

JNIEXPORT jlong JNICALL
Java_dev_ffmpegkit_whisper_WhisperJNI_nativeLoadModel(JNIEnv *env, jobject, jstring jpath) {
    const std::string path = jstr(env, jpath);
    whisper_context_params cparams = whisper_context_default_params();
    whisper_context *ctx = whisper_init_from_file_with_params(path.c_str(), cparams);
    if (!ctx) {
        LOGE("failed to load model: %s", path.c_str());
        return 0;
    }
    LOGI("model loaded: %s", path.c_str());
    return reinterpret_cast<jlong>(ctx);
}

JNIEXPORT jstring JNICALL
Java_dev_ffmpegkit_whisper_WhisperJNI_nativeTranscribe(
        JNIEnv *env, jobject, jlong handle, jstring jaudio,
        jstring jlang, jboolean translate, jint threads) {

    auto *ctx = reinterpret_cast<whisper_context *>(handle);
    if (!ctx) return env->NewStringUTF("{\"error\":\"invalid model handle\"}");

    std::vector<float> pcmf32;
    if (!read_wav_pcm16(jstr(env, jaudio).c_str(), pcmf32)) {
        return env->NewStringUTF("{\"error\":\"could not read audio (expected 16-bit PCM WAV, 16 kHz mono)\"}");
    }

    const std::string lang = jstr(env, jlang);
    whisper_full_params wparams = whisper_full_default_params(WHISPER_SAMPLING_GREEDY);
    wparams.language       = (lang == "auto" || lang.empty()) ? nullptr : lang.c_str();
    wparams.translate      = translate == JNI_TRUE;
    wparams.n_threads      = threads > 0 ? threads : 4;
    wparams.print_progress = false;
    wparams.print_realtime = false;

    if (whisper_full(ctx, wparams, pcmf32.data(), static_cast<int>(pcmf32.size())) != 0) {
        return env->NewStringUTF("{\"error\":\"transcription failed\"}");
    }

    // Build JSON: { "text": "...", "segments": [ {start,end,text}, ... ] }
    const int n = whisper_full_n_segments(ctx);
    std::string full, segments;
    for (int i = 0; i < n; ++i) {
        const char *seg = whisper_full_get_segment_text(ctx, i);
        const int64_t t0 = whisper_full_get_segment_t0(ctx, i) * 10; // centiseconds → ms
        const int64_t t1 = whisper_full_get_segment_t1(ctx, i) * 10;
        full += seg ? seg : "";
        if (i) segments += ",";
        segments += "{\"startMs\":" + std::to_string(t0) +
                    ",\"endMs\":"   + std::to_string(t1) +
                    ",\"text\":\""  + json_escape(seg ? seg : "") + "\"}";
    }

    const std::string out = "{\"text\":\"" + json_escape(full) +
                            "\",\"segments\":[" + segments + "]}";
    return env->NewStringUTF(out.c_str());
}

JNIEXPORT void JNICALL
Java_dev_ffmpegkit_whisper_WhisperJNI_nativeReleaseModel(JNIEnv *, jobject, jlong handle) {
    auto *ctx = reinterpret_cast<whisper_context *>(handle);
    if (ctx) whisper_free(ctx);
}

JNIEXPORT jstring JNICALL
Java_dev_ffmpegkit_whisper_WhisperJNI_nativeGetSystemInfo(JNIEnv *env, jobject) {
    return env->NewStringUTF(whisper_print_system_info());
}

} // extern "C"
