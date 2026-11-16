#include <jni.h>
#include <android/log.h>
#include <algorithm>
#include <atomic>
#include <cmath>
#include <mutex>
#include <string>
#include <thread>
#include <vector>
#include <whisper.h>

#define LOG_TAG "LsbWhisper"
#define LOGI(...) __android_log_print(ANDROID_LOG_INFO, LOG_TAG, __VA_ARGS__)
#define LOGE(...) __android_log_print(ANDROID_LOG_ERROR, LOG_TAG, __VA_ARGS__)

namespace {

struct EngineState {
    whisper_context* ctx = nullptr;
    std::atomic<bool> cancelled{false};
    std::atomic<int> progress{0};
    std::mutex mutex;
};

bool abortCallback(void* userData) {
    auto* state = static_cast<EngineState*>(userData);
    return state != nullptr && state->cancelled.load();
}

void progressCallback(struct whisper_context*, struct whisper_state*, int progress, void* userData) {
    auto* state = static_cast<EngineState*>(userData);
    if (state != nullptr) {
        state->progress.store(progress);
    }
}

std::string jsonEscape(const std::string& input) {
    std::string out;
    out.reserve(input.size());
    for (char c : input) {
        switch (c) {
            case '\\': out += "\\\\"; break;
            case '"': out += "\\\""; break;
            case '\n': out += "\\n"; break;
            case '\r': out += "\\r"; break;
            case '\t': out += "\\t"; break;
            default: out += c; break;
        }
    }
    return out;
}

bool isSpecialToken(whisper_context* ctx, whisper_token token) {
    return token == whisper_token_eot(ctx) ||
           token == whisper_token_sot(ctx) ||
           token == whisper_token_solm(ctx) ||
           token == whisper_token_not(ctx) ||
           token == whisper_token_beg(ctx);
}

jstring toJString(JNIEnv* env, const std::string& value) {
    return env->NewStringUTF(value.c_str());
}

}  // namespace

extern "C" JNIEXPORT jlong JNICALL
Java_pt_lersembarreiras_speech_WhisperJni_nativeInit(
        JNIEnv* env, jobject /*thiz*/, jstring modelPath, jint nThreads) {
    const char* path = env->GetStringUTFChars(modelPath, nullptr);
    whisper_context_params cparams = whisper_context_default_params();
    cparams.use_gpu = false;
    cparams.flash_attn = false;
    LOGI("A carregar modelo Whisper a partir de %s (threads=%d)", path, nThreads);
    whisper_context* ctx = whisper_init_from_file_with_params(path, cparams);
    env->ReleaseStringUTFChars(modelPath, path);
    if (ctx == nullptr) {
        LOGE("Falha ao carregar o modelo Whisper.");
        return 0;
    }
    auto* state = new EngineState();
    state->ctx = ctx;
    return reinterpret_cast<jlong>(state);
}

extern "C" JNIEXPORT void JNICALL
Java_pt_lersembarreiras_speech_WhisperJni_nativeFree(
        JNIEnv* /*env*/, jobject /*thiz*/, jlong handle) {
    auto* state = reinterpret_cast<EngineState*>(handle);
    if (state == nullptr) return;
    std::lock_guard<std::mutex> lock(state->mutex);
    if (state->ctx != nullptr) {
        whisper_free(state->ctx);
        state->ctx = nullptr;
    }
    delete state;
}

extern "C" JNIEXPORT void JNICALL
Java_pt_lersembarreiras_speech_WhisperJni_nativeCancel(
        JNIEnv* /*env*/, jobject /*thiz*/, jlong handle) {
    auto* state = reinterpret_cast<EngineState*>(handle);
    if (state != nullptr) {
        state->cancelled.store(true);
    }
}

extern "C" JNIEXPORT jint JNICALL
Java_pt_lersembarreiras_speech_WhisperJni_nativeProgress(
        JNIEnv* /*env*/, jobject /*thiz*/, jlong handle) {
    auto* state = reinterpret_cast<EngineState*>(handle);
    if (state == nullptr) return 0;
    return state->progress.load();
}

extern "C" JNIEXPORT jstring JNICALL
Java_pt_lersembarreiras_speech_WhisperJni_nativeTranscribe(
        JNIEnv* env,
        jobject /*thiz*/,
        jlong handle,
        jfloatArray samples,
        jstring language,
        jstring prompt) {
    auto* state = reinterpret_cast<EngineState*>(handle);
    if (state == nullptr || state->ctx == nullptr) {
        return toJString(env, "{\"error\":\"modelo nao carregado\",\"words\":[]}");
    }

    std::lock_guard<std::mutex> lock(state->mutex);
    state->cancelled.store(false);
    state->progress.store(0);

    const jsize nSamples = env->GetArrayLength(samples);
    jfloat* sampleData = env->GetFloatArrayElements(samples, nullptr);
    const char* langChars = env->GetStringUTFChars(language, nullptr);
    const char* promptChars = prompt != nullptr ? env->GetStringUTFChars(prompt, nullptr) : nullptr;

    whisper_full_params params = whisper_full_default_params(WHISPER_SAMPLING_GREEDY);
    params.language = langChars;
    params.translate = false;
    params.print_progress = false;
    params.print_realtime = false;
    params.print_timestamps = false;
    params.print_special = false;
    params.no_context = true;
    params.single_segment = false;
    params.token_timestamps = true;
    params.max_len = 60;
    params.split_on_word = true;
    params.suppress_blank = true;
    params.n_threads = std::max(1, std::min(6, (int) std::thread::hardware_concurrency()));
    params.abort_callback = abortCallback;
    params.abort_callback_user_data = state;
    params.progress_callback = progressCallback;
    params.progress_callback_user_data = state;
    if (promptChars != nullptr && promptChars[0] != '\0') {
        params.initial_prompt = promptChars;
    }

    LOGI("whisper_full: %d amostras, lingua=%s, token_timestamps=1", nSamples, langChars);
    const int rc = whisper_full(state->ctx, params, sampleData, nSamples);

    env->ReleaseFloatArrayElements(samples, sampleData, JNI_ABORT);
    env->ReleaseStringUTFChars(language, langChars);
    if (promptChars != nullptr) {
        env->ReleaseStringUTFChars(prompt, promptChars);
    }

    if (state->cancelled.load()) {
        return toJString(env, "{\"cancelled\":true,\"words\":[]}");
    }
    if (rc != 0) {
        LOGE("whisper_full falhou com código %d", rc);
        return toJString(env, "{\"error\":\"falha no reconhecimento\",\"words\":[]}");
    }

    std::string json = "{\"words\":[";
    bool first = true;
    const int nSegments = whisper_full_n_segments(state->ctx);
    for (int s = 0; s < nSegments; ++s) {
        const int nTokens = whisper_full_n_tokens(state->ctx, s);
        std::string currentWord;
        int64_t wordT0 = -1;
        int64_t wordT1 = -1;
        float probSum = 0.f;
        int probCount = 0;
        const int64_t segT0 = whisper_full_get_segment_t0(state->ctx, s);
        const int64_t segT1 = whisper_full_get_segment_t1(state->ctx, s);

        auto flushWord = [&]() {
            if (currentWord.empty()) return;
            while (!currentWord.empty() && currentWord.front() == ' ') currentWord.erase(currentWord.begin());
            while (!currentWord.empty() && currentWord.back() == ' ') currentWord.pop_back();
            if (currentWord.empty()) return;
            int64_t start = wordT0 >= 0 ? wordT0 : segT0;
            int64_t end = wordT1 >= 0 ? wordT1 : segT1;
            if (end < start) end = start;
            const float probability = probCount > 0 ? probSum / static_cast<float>(probCount) : 0.f;
            if (!first) json += ",";
            first = false;
            json += "{\"text\":\"" + jsonEscape(currentWord) + "\"";
            json += ",\"startMs\":" + std::to_string(start * 10);
            json += ",\"endMs\":" + std::to_string(end * 10);
            json += ",\"probability\":" + std::to_string(probability);
            json += ",\"tokenTimestamps\":";
            json += (wordT0 >= 0 && wordT1 > wordT0) ? "true" : "false";
            json += "}";
            currentWord.clear();
            wordT0 = -1;
            wordT1 = -1;
            probSum = 0.f;
            probCount = 0;
        };

        for (int t = 0; t < nTokens; ++t) {
            const whisper_token_data data = whisper_full_get_token_data(state->ctx, s, t);
            if (isSpecialToken(state->ctx, data.id)) continue;
            const char* tokenText = whisper_full_get_token_text(state->ctx, s, t);
            if (tokenText == nullptr || tokenText[0] == '\0') continue;
            std::string piece(tokenText);
            const bool newWord = !currentWord.empty() && (piece[0] == ' ' || piece[0] == '\t');
            if (newWord) {
                flushWord();
            }
            currentWord += piece;
            if (wordT0 < 0) wordT0 = data.t0;
            if (data.t1 > 0) wordT1 = data.t1;
            probSum += data.p;
            probCount++;
        }
        flushWord();
    }
    json += "],\"segmentCount\":" + std::to_string(nSegments) + "}";
    state->progress.store(100);
    return toJString(env, json);
}
