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

// whisper.cpp's integrated audio decoder (v1.7.5 ships miniaudio, which embeds
// ma_dr_wav). No FFmpeg. Decode-only build (MA_NO_DEVICE_IO): decodes WAV/MP3/
// FLAC and resamples to 16 kHz mono f32 — exactly what whisper_full expects.
#define MINIAUDIO_IMPLEMENTATION
#define MA_NO_DEVICE_IO
#define MA_NO_ENGINE
#define MA_NO_RESOURCE_MANAGER
#define MA_NO_NODE_GRAPH
#define MA_NO_GENERATION
#include "miniaudio.h"

#define LOG_TAG "whisper-jni"
#define LOGI(...) __android_log_print(ANDROID_LOG_INFO, LOG_TAG, __VA_ARGS__)
#define LOGE(...) __android_log_print(ANDROID_LOG_ERROR, LOG_TAG, __VA_ARGS__)

namespace {

// Decode any WAV/MP3/FLAC file to mono f32 @ 16 kHz via miniaudio (whisper.cpp's
// integrated decoder). Handles arbitrary channel counts / sample rates by
// downmixing + resampling. No FFmpeg dependency.
bool decode_pcmf32(const char *path, std::vector<float> &pcmf32) {
    ma_decoder_config cfg = ma_decoder_config_init(ma_format_f32, 1, WHISPER_SAMPLE_RATE);
    ma_decoder decoder;
    if (ma_decoder_init_file(path, &cfg, &decoder) != MA_SUCCESS) {
        LOGE("could not open audio: %s", path);
        return false;
    }

    pcmf32.clear();
    constexpr ma_uint64 CHUNK = 4096; // frames == floats (mono)
    std::vector<float> buf(CHUNK);
    ma_uint64 read = 0;
    while (ma_decoder_read_pcm_frames(&decoder, buf.data(), CHUNK, &read) == MA_SUCCESS && read > 0) {
        pcmf32.insert(pcmf32.end(), buf.begin(), buf.begin() + static_cast<size_t>(read));
        if (read < CHUNK) break;
    }

    ma_decoder_uninit(&decoder);
    return !pcmf32.empty();
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
    if (!decode_pcmf32(jstr(env, jaudio).c_str(), pcmf32)) {
        return env->NewStringUTF("{\"error\":\"could not decode audio (supported: WAV/MP3/FLAC)\"}");
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
