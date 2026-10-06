# Architecture and offline contract — 0.3.0

Text or system speech or bundled Latin OCR produces an input sentence. Bundled ML Kit Language ID detects the source unless the user has selected a source manually. TranslationRepository builds a single-turn HY prompt for each selected target and calls HyTranslationEngine, which serializes llama.cpp JNI inference. Completed targets appear progressively. Each result has independent system offline TTS.

## Translation model

The exact Hy-MT2-1.8B Q4_K_M GGUF is an uncompressed APK asset. Gradle checks its size and SHA-256 before building. BundledModelStore copies it to noBackupFilesDir/llm, hashes all bytes, and atomically renames the partial file only after validation. It verifies existing data once per process. Retry never treats a partial file as a usable model.

This duplicates model storage between the APK and the extracted file, but permits the native engine to use mmap with a normal private file path. No network permission or translation model downloader is present in this build.

## Native inference

- llama.cpp v0.5.0, commit 7fe450e19305b828c199d602c23a8337aaa1f03b; Android NDK r28c.
- Portable ARM64 CPU baseline, four threads maximum; no GPU/NPU claims.
- One shared model and context per repository, sequential target generation.
- 4096-token context; at most 1200 input characters and 768 generated tokens. Exceeding limits returns an error instead of silently truncating a translation.
- Exact Hunyuan Dense single-turn chat tokens from GGUF metadata. User content is tokenized with parse_special=false; UTF-8 output pieces are combined as bytes.
- Clear model memory before every request. Prefill yields between 128-token chunks and decode yields after every token for cancellation checks.
- Kotlin mutex serializes native model access. Close waits for outstanding native calls before freeing the model.

## Offline boundary

The merged application manifest removes INTERNET and ACCESS_NETWORK_STATE, including transitive dependency declarations. Translation and model preparation cannot fetch remote weights or call cloud translation. There is no ML Kit Translation fallback. ML Kit remains only for bundled language identification and OCR.

System speech is created only with createOnDeviceSpeechRecognizer. It never falls back to a generic recognizer. System TTS filters network-required/uninstalled voices. Those system services are outside the application's process; installation of their language packs can involve system-controlled networking. Bundling HY does not provide ASR or TTS weights.

## State and lifecycle

A new input clears previous translations immediately. Old speech session callbacks cannot replace new text. Model preparation and translation publish progress independently. Each completed target is displayed before remaining targets finish; a later target failure leaves already completed results for that same input visible with an error.

Manual source selection overrides speech hints and language detection. OCR resets source selection to automatic. Returning from system settings refreshes permissions and speech/TTS inventory. Backgrounding stops microphone and TTS. Captured photos are not saved.

## Device verification

The development test device is iQOO 11 Pro; the actual-use device is vivo X300 Pro / Dimensity 9500. Successful inference on the former does not establish performance or system speech availability on the latter. See BUNDLED_HY_BUILD.md for reproducible build inputs and DEVICE_TEST_PLAN.md for device results.
