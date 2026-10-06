#include <jni.h>
#include <android/log.h>
#include "llama.h"
#include "mtmd.h"
#include "mtmd-helper.h"
#include <algorithm>
#include <memory>
#include <mutex>
#include <stdexcept>
#include <string>
#include <vector>

namespace {
struct Session {
    llama_model * model = nullptr;
    llama_context * context = nullptr;
    llama_sampler * sampler = nullptr;
    const llama_vocab * vocab = nullptr;
    std::vector<llama_token> input;
    size_t offset = 0;
    int generated = 0;
    int limit = 0;
    bool active = false;
    bool speech = false;
    ~Session() {
        if (sampler) llama_sampler_free(sampler);
        if (context) llama_free(context);
        if (model) llama_model_free(model);
    }
};

void fail(JNIEnv * env, const char * message) {
    if (!env->ExceptionCheck()) env->ThrowNew(env->FindClass("java/lang/IllegalStateException"), message);
}

Session & session(jlong handle) {
    if (!handle) throw std::runtime_error("LLM inference engine is not loaded");
    return *reinterpret_cast<Session *>(handle);
}

std::vector<llama_token> tokenize(const llama_vocab * vocab, const std::string & text, bool special) {
    int count = llama_tokenize(vocab, text.data(), static_cast<int>(text.size()), nullptr, 0, false, special);
    if (count >= 0) return {};
    std::vector<llama_token> tokens(-count);
    count = llama_tokenize(vocab, text.data(), static_cast<int>(text.size()), tokens.data(),
                           static_cast<int>(tokens.size()), false, special);
    if (count < 0) throw std::runtime_error("LLM prompt tokenization failed");
    tokens.resize(count);
    return tokens;
}
}

extern "C" JNIEXPORT jlong JNICALL
Java_com_tuttochiaro_offline_llm_NativeLlm_load(JNIEnv * env, jobject, jstring path, jint threads) {
    try {
        static std::once_flag initialized;
        std::call_once(initialized, [] {
            llama_log_set([](ggml_log_level level, const char * text, void *) {
                if (level >= GGML_LOG_LEVEL_WARN) __android_log_write(ANDROID_LOG_WARN, "TuttoLLM", text);
            }, nullptr);
            llama_backend_init();
            ggml_backend_load_all();
        });
        const char * chars = env->GetStringUTFChars(path, nullptr);
        if (!chars) return 0;
        std::string file(chars);
        env->ReleaseStringUTFChars(path, chars);
        auto s = std::make_unique<Session>();
        auto modelParams = llama_model_default_params();
        modelParams.n_gpu_layers = 0; // Portable CPU baseline for both target phones.
        modelParams.load_mode = LLAMA_LOAD_MODE_MMAP;
        s->model = llama_model_load_from_file(file.c_str(), modelParams);
        if (!s->model) throw std::runtime_error("Cannot load bundled LLM; check available memory");
        s->vocab = llama_model_get_vocab(s->model);
        auto parameters = llama_context_default_params();
        parameters.n_ctx = 4096;
        parameters.n_batch = 128;
        parameters.n_ubatch = 128;
        parameters.n_threads = std::clamp(static_cast<int>(threads), 1, 4);
        parameters.n_threads_batch = parameters.n_threads;
        s->context = llama_init_from_model(s->model, parameters);
        if (!s->context) throw std::runtime_error("Cannot allocate LLM inference context; close other apps and retry");
        s->sampler = llama_sampler_init_greedy();
        __android_log_write(ANDROID_LOG_INFO, "TuttoLLM", "LLM loaded: CPU, ctx=4096");
        return reinterpret_cast<jlong>(s.release());
    } catch (const std::exception & error) { fail(env, error.what()); return 0; }
}

extern "C" JNIEXPORT void JNICALL
Java_com_tuttochiaro_offline_llm_NativeLlm_begin(JNIEnv * env, jobject, jlong handle, jbyteArray prompt, jint maxTokens, jint chatTemplate) {
    try {
        auto & s = session(handle);
        s.active = false;
        const auto length = env->GetArrayLength(prompt);
        std::string content(length, '\0');
        env->GetByteArrayRegion(prompt, 0, length, reinterpret_cast<jbyte *>(content.data()));
        if (env->ExceptionCheck()) return;
        // Exact single-user template from the bundled GGUF. Parse role tokens separately
        // so literal special-token text in the user's input is treated only as content.
        const std::string prefix = chatTemplate == 1 ? "<|im_start|>user\n" : u8"<\uff5chy_begin\u2581of\u2581sentence\uff5c><\uff5chy_User\uff5c>";
        const std::string suffix = chatTemplate == 1 ? "<|im_end|>\n<|im_start|>assistant\n<think>\n\n</think>\n\n" : u8"<\uff5chy_Assistant\uff5c>";
        auto input = tokenize(s.vocab, prefix, true);
        auto body = tokenize(s.vocab, content, false);
        auto ending = tokenize(s.vocab, suffix, true);
        if (chatTemplate == 0 && (input.size() != 2 || ending.size() != 1)) throw std::runtime_error("Unexpected HY chat template tokens");
        input.insert(input.end(), body.begin(), body.end());
        input.insert(input.end(), ending.begin(), ending.end());
        if (maxTokens < 1 || input.size() + maxTokens > llama_n_ctx(s.context))
            throw std::runtime_error("Text exceeds LLM context capacity; translate a shorter passage");
        llama_memory_clear(llama_get_memory(s.context), true);
        llama_sampler_reset(s.sampler);
        s.input = std::move(input);
        s.offset = 0;
        s.generated = 0;
        s.limit = maxTokens;
        s.speech = false;
        s.active = true;
    } catch (const std::exception & error) { fail(env, error.what()); }
}

extern "C" JNIEXPORT void JNICALL
Java_com_tuttochiaro_offline_llm_NativeLlm_beginAudio(JNIEnv * env, jobject, jlong handle,
                                                    jstring projector, jfloatArray samples, jstring language) {
    try {
        auto & s = session(handle);
        s.active = false;
        const char * chars = env->GetStringUTFChars(projector, nullptr);
        if (!chars) return;
        const std::string file(chars);
        env->ReleaseStringUTFChars(projector, chars);
        const char * hintChars = env->GetStringUTFChars(language, nullptr);
        if (!hintChars) return;
        const std::string hint(hintChars);
        env->ReleaseStringUTFChars(language, hintChars);
        const int count = env->GetArrayLength(samples);
        if (count < 1600 || count > 320000) throw std::runtime_error("Audio duration outside supported range");
        std::vector<float> audio(count);
        env->GetFloatArrayRegion(samples, 0, count, audio.data());
        if (env->ExceptionCheck()) return;
        auto params = mtmd_context_params_default();
        params.use_gpu = false;
        params.n_threads = 4;
        params.warmup = false;
        std::unique_ptr<mtmd_context, decltype(&mtmd_free)> media(
            mtmd_init_from_file(file.c_str(), s.model, params), mtmd_free);
        if (!media || !mtmd_support_audio(media.get()) || mtmd_get_audio_sample_rate(media.get()) != 16000)
            throw std::runtime_error("Cannot load Qwen audio encoder");
        std::unique_ptr<mtmd_bitmap, decltype(&mtmd_bitmap_free)> bitmap(
            mtmd_bitmap_init_from_audio(audio.size(), audio.data()), mtmd_bitmap_free);
        if (!bitmap) throw std::runtime_error("Cannot prepare audio input");
        std::unique_ptr<mtmd_input_chunks, decltype(&mtmd_input_chunks_free)> chunks(
            mtmd_input_chunks_init(), mtmd_input_chunks_free);
        std::string prompt = std::string("<|im_start|>system\n<|im_end|>\n<|im_start|>user\n") +
            mtmd_default_marker() + "<|im_end|>\n<|im_start|>assistant\n";
        if (!hint.empty()) prompt += "language " + hint + "<asr_text>";
        mtmd_input_text text{prompt.data(), prompt.size(), false, true};
        const mtmd_bitmap * bitmaps[] = {bitmap.get()};
        if (mtmd_tokenize(media.get(), chunks.get(), &text, bitmaps, 1) != 0)
            throw std::runtime_error("Cannot tokenize Qwen audio input");
        if (mtmd_helper_get_n_pos(chunks.get()) + 512 > llama_n_ctx(s.context))
            throw std::runtime_error("Audio exceeds Qwen context capacity");
        llama_memory_clear(llama_get_memory(s.context), true);
        llama_sampler_reset(s.sampler);
        llama_pos position = 0;
        if (mtmd_helper_eval_chunks(media.get(), s.context, chunks.get(), 0, 0, 128, true, &position) != 0)
            throw std::runtime_error("Qwen audio encoding failed");
        s.input.clear();
        s.offset = 0;
        s.generated = 0;
        s.limit = 512;
        s.speech = true;
        s.active = true;
        // Projector and temporary audio are freed here; only text KV state remains.
    } catch (const std::exception & error) { fail(env, error.what()); }
}

extern "C" JNIEXPORT jbyteArray JNICALL
Java_com_tuttochiaro_offline_llm_NativeLlm_next(JNIEnv * env, jobject, jlong handle) {
    try {
        auto & s = session(handle);
        if (!s.active) return nullptr;
        if (s.offset < s.input.size()) {
            const int count = static_cast<int>(std::min<size_t>(128, s.input.size() - s.offset));
            auto batch = llama_batch_get_one(s.input.data() + s.offset, count);
            if (llama_decode(s.context, batch) != 0) throw std::runtime_error("LLM prompt processing failed");
            s.offset += count;
            // Yield back to Kotlin between prefill chunks to check cancellation.
            return env->NewByteArray(0);
        }
        const auto token = llama_sampler_sample(s.sampler, s.context, -1);
        if (llama_vocab_is_eog(s.vocab, token)) {
            s.active = false;
            return nullptr;
        }
        if (s.generated >= s.limit) {
            s.active = false;
            throw std::runtime_error("LLM output limit reached; translate a shorter passage");
        }
        std::vector<char> piece(256);
        int length = llama_token_to_piece(s.vocab, token, piece.data(), piece.size(), 0, s.speech);
        if (length < 0) {
            piece.resize(-length);
            length = llama_token_to_piece(s.vocab, token, piece.data(), piece.size(), 0, s.speech);
        }
        if (length < 0) throw std::runtime_error("LLM text decoding failed");
        auto nextToken = token;
        auto batch = llama_batch_get_one(&nextToken, 1);
        if (llama_decode(s.context, batch) != 0) throw std::runtime_error("LLM generation failed");
        ++s.generated;
        auto output = env->NewByteArray(length);
        if (output) env->SetByteArrayRegion(output, 0, length, reinterpret_cast<const jbyte *>(piece.data()));
        return output;
    } catch (const std::exception & error) { fail(env, error.what()); return nullptr; }
}

extern "C" JNIEXPORT void JNICALL
Java_com_tuttochiaro_offline_llm_NativeLlm_release(JNIEnv *, jobject, jlong handle) {
    delete reinterpret_cast<Session *>(handle);
}
