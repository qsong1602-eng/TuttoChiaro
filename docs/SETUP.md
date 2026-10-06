# Build from a fresh clone

The Git repository contains source and reviewed runtime dependencies. Models, SDKs, caches, signing keys and install packages are restored locally. Downloads need Internet access on the build computer; the installed Android app runs offline and needs no API key.

## 1. Configure the Windows build computer

Use Python 3.11 or newer, Git and PowerShell. The current packaging scripts target Windows and ARM64 Android phones (Android 8 / API 26 or newer).

The supplied scripts expect this directory layout inside the project:

```text
.tooling/
  android-sdk/
    platforms/android-35/
    build-tools/35.0.0/
    platform-tools/
    ndk/28.2.13676358/
    cmake/3.22.1/
  jdk17/jdk-17.0.20+8/
    bin/java.exe
```

Install [Android SDK command-line tools](https://developer.android.com/studio#command-tools) into `.tooling/android-sdk/cmdline-tools/latest/`, then accept licenses and install the packages:

```powershell
# Run from the repository root. Point JAVA_HOME at your installed JDK 17 first.
& ./.tooling/android-sdk/cmdline-tools/latest/bin/sdkmanager.bat --sdk_root="$PWD/.tooling/android-sdk" --licenses
& ./.tooling/android-sdk/cmdline-tools/latest/bin/sdkmanager.bat --sdk_root="$PWD/.tooling/android-sdk" "platform-tools" "platforms;android-35" "build-tools;35.0.0" "ndk;28.2.13676358" "cmake;3.22.1"
```

Place JDK 17 at `.tooling/jdk17/jdk-17.0.20+8/`; the helper scripts use that directory name explicitly. You can obtain a Windows JDK 17 archive from [Eclipse Temurin](https://adoptium.net/temurin/releases/?version=17). If using a different JDK installation, adapt the helper paths or supply the expected directory locally. Do not commit the JDK or SDK.

Copy `local.properties.example` to `local.properties`. Set `sdk.dir` to the absolute SDK path on your own machine, using forward slashes. `local.properties` is ignored. Gradle 8.11.1 is downloaded and checksum-checked by the included wrapper; no separate Gradle installation is required.

```powershell
Copy-Item local.properties.example local.properties
./tools/setup-llama.ps1
```

`setup-llama.ps1` downloads the checksum-pinned llama.cpp source revision `7fe450e19305b828c199d602c23a8337aaa1f03b` under `.tooling/llama-pinned/`. CMake requires this exact location.

## 2. Restore the models locally

```powershell
python -X utf8 tools/download_offline_suite.py
```

The downloader restores these files under `models/` and validates their byte lengths and SHA-256 checksums before publishing them as complete files:

| Purpose | Local path under `models/` | Upstream repository |
|---|---|---|
| HY translation | `Hy-MT2-1.8B-Q4_K_M.gguf` | [tencent/Hy-MT2-1.8B-GGUF](https://huggingface.co/tencent/Hy-MT2-1.8B-GGUF) |
| Qwen translation | `Qwen3.5-4B-Q4_K_M.gguf` | [unsloth/Qwen3.5-4B-GGUF](https://huggingface.co/unsloth/Qwen3.5-4B-GGUF) |
| Qwen speech decoder | `qwen_asr/Qwen3-ASR-1.7B-Q8_0.gguf` | [ggml-org/Qwen3-ASR-1.7B-GGUF](https://huggingface.co/ggml-org/Qwen3-ASR-1.7B-GGUF) |
| Qwen audio encoder | `qwen_asr/mmproj-Qwen3-ASR-1.7B-bf16.gguf` | Same Qwen ASR repository |
| Whisper encoder | `whisper/turbo-encoder.int8.onnx` | [csukuangfj/sherpa-onnx-whisper-turbo](https://huggingface.co/csukuangfj/sherpa-onnx-whisper-turbo) |
| Whisper decoder | `whisper/turbo-decoder.int8.onnx` | Same Whisper repository |
| Whisper vocabulary | `whisper/turbo-tokens.txt` | Same Whisper repository |

Qwen and Whisper revisions, sizes and checksums are recorded in the checked-in `models/suite-lock.json`. HY is restored from the official repository's `main` URL with the exact checksum `dc5f44fcf1fa496ee7ad725982c0c8c553a4de00259b53af84c4b89fb0c06699`; a changed upstream file is rejected. HY's size is 1,133,080,448 bytes. The packaging tool inserts this same HY manifest entry separately.

You may copy already downloaded files into the listed paths, but they must match these checksums. Download fragments remain ignored. Do not replace the Qwen ASR pair with Qwen text weights or use the old experimental ONNX ASR files. Renaming another model to the expected filename does not make it compatible.

Weights total roughly 7.7 GB. Allow additional computer storage for download fragments, Gradle/native build output and the packaged splits, which duplicate the weights. The phone should have at least about 18 GB free. The repository includes model and inference-library license texts; retain them when distributing an installation suite and review each upstream model's terms.

## 3. Speech runtime and first build

The build uses the checked-in `app/libs/sherpa-onnx-1.13.8-utf8fix.aar`. This archive contains the Kotlin API, ARM64 ONNX Runtime and patched sherpa JNI; it contains no model weights. Its SHA-256 is `a04b1032510dfc7ea4396999280b4a25cc40f5cb983eccde17f4ec0def3ca81a`.

Provenance and original/native checksums are in `docs/SHERPA_UTF8_FIX_BUILD.json`; patches are in `tools/patches/`. Upstream is [k2-fsa/sherpa-onnx](https://github.com/k2-fsa/sherpa-onnx). The UTF-8 repair joins Whisper BPE bytes before normalizing complete text. The original unpatched AAR and native build cache are omitted. Rebuilding the patched AAR is an optional advanced task described in `OFFLINE_SUITE_BUILD.md`, not a prerequisite for building the app from this repository.

Stage the model manifest, then perform the first Gradle build **online** to populate the local dependency cache:

```powershell
python -X utf8 tools/package-offline-suite.py stage
$env:JAVA_HOME = "$PWD/.tooling/jdk17/jdk-17.0.20+8"
$env:GRADLE_USER_HOME = "$PWD/.tooling/gradle-user-home"
$env:ANDROID_USER_HOME = "$PWD/.tooling/android-user-home"
$env:TEMP = "$PWD/.tooling/temp"
$env:TMP = $env:TEMP
New-Item -ItemType Directory -Force -Path $env:TEMP | Out-Null
$env:CMAKE_BUILD_PARALLEL_LEVEL = '4'
./gradlew.bat :app:lintDebug :app:testDebugUnitTest :app:assembleDebug --no-daemon --max-workers=1
python -X utf8 tools/package-offline-suite.py package
python -X utf8 tools/verify-offline-suite.py
```

Later, use `./tools/build-bundled.ps1` for the cached offline build. This helper deliberately passes `--offline`, so it cannot populate a missing Maven cache on a fresh clone. Add `-IncludeDeviceTests` when building the instrumentation APK. Four public FLEURS test recordings and their attribution/reference metadata are included under `app/src/androidTest/assets/speech/`; these are engineering fixtures, not personal recordings or model weights.

`verify-offline-suite.py` checks matching APK signatures, permission boundaries, native libraries and reconstructed model hashes. It writes an ignored local verification report. To additionally verify upgrade-signature compatibility with a previous installation package, pass `--upgrade-from <path-to-previous.apk>`. A new clone generates its own local debug signing key; it cannot update an old installation signed with another computer's key. Keep any signing key private.

## 4. Install and select models

Unlock the phone, enable USB debugging, authorize the computer and permit the phone's installation prompt:

```powershell
./TuttoChiaro-offline-v0.8.0/install.ps1 -AdbPath "./.tooling/android-sdk/platform-tools/adb.exe"
```

Install the base APK and every model split together using the installer. The base APK alone does not contain usable translation/speech weights. In the app's Settings, select a translation model (HY or Qwen) and a speech model (Qwen ASR or Whisper). Initial preparation reconstructs and verifies the local files; it does not download them. Install any desired offline TTS voices through the phone's system settings separately.

## What stays out of Git

`.gitignore` excludes all local model payloads, download fragments, packaged APKs/suites, build output, toolchain caches, `.env` files, API credentials, signing keys, personal local settings and raw diagnostics. The `models/` allowlist contains only its README, suite lock and license texts. Keep other personal material under ignored `private/`, not inside source or shared documentation. This project requires no cloud API configuration.
