package com.tuttochiaro.offline.llm

/** All methods are serialized by HyTranslationEngine; native handles never escape it. */
internal object NativeLlm {
    init { System.loadLibrary("tutto_llm") }

    external fun load(path: String, threads: Int): Long
    external fun begin(handle: Long, prompt: ByteArray, maxTokens: Int, template: Int)
    external fun beginAudio(handle: Long, projector: String, samples: FloatArray, language: String)
    external fun next(handle: Long): ByteArray?
    external fun release(handle: Long)
}
