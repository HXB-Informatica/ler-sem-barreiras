#include <jni.h>
#include <android/log.h>
#include <algorithm>
#include <atomic>
#include <cmath>
#include <mutex>
#include <string>
#include <thread>
#include <utility>
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

whisper_alignment_heads_preset alignmentHeadsFor(const std::string& modelPath) {
    const bool english = modelPath.find(".en") != std::string::npos;
    if (modelPath.find("tiny") != std::string::npos) {
        return english ? WHISPER_AHEADS_TINY_EN : WHISPER_AHEADS_TINY;
    }
    return english ? WHISPER_AHEADS_BASE_EN : WHISPER_AHEADS_BASE;
}

struct DtwWord {
    std::string text;
    int64_t endCs = -1;
    float probSum = 0.f;
    int probCount = 0;
};

void trimSpaces(std::string& word) {
    while (!word.empty() && (word.front() == ' ' || word.front() == '\t')) word.erase(word.begin());
    while (!word.empty() && (word.back() == ' ' || word.back() == '\t')) word.pop_back();
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
    cparams.dtw_token_timestamps = true;
    cparams.dtw_aheads_preset = alignmentHeadsFor(path);
    LOGI("A carregar modelo Whisper a partir de %s (threads=%d, dtw)", path, nThreads);
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
    (void) prompt;

    whisper_full_params params = whisper_full_default_params(WHISPER_SAMPLING_GREEDY);
    params.language = langChars;
    params.translate = false;
    params.print_progress = false;
    params.print_realtime = false;
    params.print_timestamps = false;
    params.print_special = false;
    params.no_context = true;
    params.single_segment = false;
    params.token_timestamps = false;
    params.max_len = 0;
    params.split_on_word = false;
    params.suppress_blank = true;
    params.n_threads = std::max(1, std::min(6, (int) std::thread::hardware_concurrency()));
    params.abort_callback = abortCallback;
    params.abort_callback_user_data = state;
    params.progress_callback = progressCallback;
    params.progress_callback_user_data = state;

    LOGI("whisper_full: %d amostras, lingua=%s, dtw", nSamples, langChars);
    const int rc = whisper_full(state->ctx, params, sampleData, nSamples);

    env->ReleaseFloatArrayElements(samples, sampleData, JNI_ABORT);
    env->ReleaseStringUTFChars(language, langChars);

    if (state->cancelled.load()) {
        return toJString(env, "{\"cancelled\":true,\"words\":[]}");
    }
    if (rc != 0) {
        LOGE("whisper_full falhou com código %d", rc);
        return toJString(env, "{\"error\":\"falha no reconhecimento\",\"words\":[]}");
    }

    std::vector<DtwWord> words;
    bool sawDtw = false;
    const int nSegments = whisper_full_n_segments(state->ctx);
    for (int s = 0; s < nSegments; ++s) {
        const int nTokens = whisper_full_n_tokens(state->ctx, s);
        for (int t = 0; t < nTokens; ++t) {
            const whisper_token_data data = whisper_full_get_token_data(state->ctx, s, t);
            if (data.id >= whisper_token_beg(state->ctx) || isSpecialToken(state->ctx, data.id)) continue;
            const char* tokenText = whisper_full_get_token_text(state->ctx, s, t);
            if (tokenText == nullptr || tokenText[0] == '\0') continue;
            const std::string piece(tokenText);
            const bool boundary = !words.empty() && (piece[0] == ' ' || piece[0] == '\t');
            if (words.empty() || boundary) words.push_back({});
            DtwWord& word = words.back();
            word.text += piece;
            if (data.t_dtw > word.endCs) word.endCs = data.t_dtw;
            if (data.t_dtw > 0) sawDtw = true;
            word.probSum += data.p;
            word.probCount++;
        }
    }

    std::vector<std::pair<int64_t, int64_t>> spans;
    spans.reserve(words.size());
    int64_t cursor = 0;
    for (const DtwWord& word : words) {
        int64_t end = word.endCs < 0 ? cursor : word.endCs;
        if (end < cursor) end = cursor;
        spans.emplace_back(cursor, end);
        if (end > cursor) cursor = end;
    }
    std::vector<int64_t> durations;
    for (const auto& span : spans) {
        if (span.second > span.first) durations.push_back(span.second - span.first);
    }
    if (!durations.empty()) {
        std::sort(durations.begin(), durations.end());
        int64_t median = durations[durations.size() / 2];
        if (median > 70) median = 70;
        const int64_t maxDuration = std::max<int64_t>(median * 2, 20);
        for (auto& span : spans) {
            if (span.second - span.first > maxDuration) {
                span.first = span.second - maxDuration;
            }
        }
    }

    std::string json = "{\"words\":[";
    bool first = true;
    for (size_t i = 0; i < words.size(); ++i) {
        trimSpaces(words[i].text);
        if (words[i].text.empty()) continue;
        const int64_t start = spans[i].first;
        const int64_t end = spans[i].second;
        if (!sawDtw || end <= start) continue;
        const float probability = words[i].probCount > 0
            ? words[i].probSum / static_cast<float>(words[i].probCount)
            : 0.f;
        if (!first) json += ",";
        first = false;
        json += "{\"text\":\"" + jsonEscape(words[i].text) + "\"";
        json += ",\"startMs\":" + std::to_string(start * 10);
        json += ",\"endMs\":" + std::to_string(end * 10);
        json += ",\"probability\":" + std::to_string(probability);
        json += ",\"tokenTimestamps\":true}";
    }
    json += "],\"segmentCount\":" + std::to_string(nSegments) + "}";
    state->progress.store(100);
    return toJString(env, json);
}
