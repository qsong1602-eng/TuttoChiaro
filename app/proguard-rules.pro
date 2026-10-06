# ML Kit publishes the consumer rules required by its models.
# Keep Kotlin coroutine continuation metadata for useful release stack traces.
-keepattributes SourceFile,LineNumberTable
-keep class com.tuttochiaro.offline.llm.NativeLlm { *; }
# The native sherpa JNI bridge resolves configuration/result fields by name.
-keep class com.k2fsa.sherpa.onnx.** { *; }
