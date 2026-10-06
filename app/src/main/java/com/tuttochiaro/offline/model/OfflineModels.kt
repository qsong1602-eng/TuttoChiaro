package com.tuttochiaro.offline.model

enum class TranslationModel(val id: String, val label: String, val file: String) {
    HY("hy", "HY · 1.8B", "Hy-MT2-1.8B-Q4_K_M.gguf"),
    QWEN("qwen", "Qwen3.5 · 4B", "Qwen3.5-4B-Q4_K_M.gguf");
}
enum class SpeechModel(val id: String, val label: String) {
    QWEN("qwen_asr", "Qwen3-ASR · 1.7B"),
    WHISPER("whisper", "Whisper large-v3-turbo");
}
