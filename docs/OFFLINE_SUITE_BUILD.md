# 0.8.0 四模型离线套件

首次克隆的环境与模型恢复步骤见 [SETUP.md](SETUP.md)。Git 仓库不含权重、安装套件、SDK/JDK 缓存或私人设备记录；下列离线构建命令要求先缓存依赖。

## 模型与安装

翻译可选 HY-MT2-1.8B Q4_K_M 与 Qwen3.5-4B Q4_K_M；语音识别可选 Qwen3-ASR-1.7B Q8_0 + BF16 音频编码器 与 Whisper large-v3-turbo INT8。Qwen3.5 是文字模型，Qwen3-ASR 是语音模型。Qwen 语音采用 ggml-org 提供的 GGUF；早期测试的社区 INT8 ONNX 因中文空输出被替换，未收入套件。

模型约 7.7 GB，分为基础 APK 和若干只含资产的 split APK，每个资产包约 1 GiB 或更小。同一应用、同一签名、同一版本号，必须一次安装全部文件。仅安装 base.apk 不构成完整离线版本。初次选用模型时从安装资产重组并 SHA-256 校验到应用私有目录；旧版已准备好的 HY 可以继续使用。

套件中不包含未完成下载、Whisper Small、原始 FP32 大文件或下载缓存。基础 APK 内包含资源清单、模型来源和许可证。安装后应用没有 Internet 权限，不会下载模型。朗读仍使用手机的离线 TTS 音色，OCR 仍为内置 Latin OCR。

## 构建顺序

```powershell
python -X utf8 tools/download_offline_suite.py
python -X utf8 tools/package-offline-suite.py stage
./tools/build-bundled.ps1 -IncludeDeviceTests
python -X utf8 tools/package-offline-suite.py package
python -X utf8 tools/verify-offline-suite.py
./TuttoChiaro-offline-v0.8.0/install.ps1
```

下载只发生在打包电脑。固定来源、版本、长度和 SHA-256 保存在 `models/suite-lock.json`；小文件同时对照固定 Git blob。下载有分块续传，只有完整校验通过才成为正式模型文件。

离线套件目录 `TuttoChiaro-offline-v0.8.0/` 含 base.apk、models_*.apk、安装脚本、校验信息和许可证。通过 USB 安装时解锁手机并允许厂商安装确认。脚本使用 `adb install-multiple -r`，保留应用数据。建议手机至少留 18 GB 可用空间用于安装、重组和安装临时空间。

构建依赖沿用 NDK r28c、CMake 3.22.1、JDK 17、固定 llama.cpp v0.5.0；Whisper 使用本项目修复 UTF-8 拼接后的 `app/libs/sherpa-onnx-1.13.8-utf8fix.aar`。修复说明和哈希见 `SHERPA_UTF8_FIX_BUILD.json`，补丁位于 `tools/patches/`。仅 ARM64，CPU 最多四线程，暂未使用 GPU/NPU。

## 运行约束

- 翻译两套模型按选择加载，不同时驻留；语音识别前释放文字模型，识别后释放语音模型再翻译。
- 输入上限 1200 字符，翻译上下文 4096、输出最多 768 tokens；输出超限明确报错。
- Qwen 使用 GGUF 单轮 ChatML 模板，关闭思考输出。用户正文以普通内容编码，不解析其中的角色控制标记。
- 语音录音保存在内存，16 kHz 单声道；单次最长 20 秒。连续试用模式每段最多 8 秒、每轮最多 120 秒，按停顿处理，队列满停止收音；不保证实时。说完点“完成收音”。取消、改写输入、切换模型、进入后台会丢弃当前录音/识别结果。
- 原生 ASR 的一次计算不可立即中断。取消后丢弃结果，等待计算返回再释放，避免释放仍在使用的 native 对象。
- Qwen ASR 手选语言通过生成前的语言提示；自动模式的文字语言可由内置 Language ID 判断。Whisper 自动模式使用内置识别语言信息。

## 验证边界

`verify-offline-suite.py` 校验全部 APK 签名、无联网权限、推理库、资产唯一性、跨分片重组后的模型完整哈希。如需校验旧版证书兼容，显式传入 `--upgrade-from <旧版APK路径>`；旧版 APK 和签名密钥不随仓库上传。结果写入本地忽略提交的 `docs/BUILD_0.8.0_VERIFICATION.json`；它只代表安装文件校验。

`OfflineSuiteTest` 包括两套 ASR 对四种语言的 FLEURS 样本转写和翻译模型切换。公开样本附 CC BY 4.0 归属与参考文字。单条样本和宽松误差门限仅用于排查空输出、乱码和严重错误，不代表旅行场景的全面准确率。真机输出应另存原始报告。iQOO 测试不能代替 vivo X300 Pro 验收。

OriginOS 测试需保持解锁和前台。测试启动后由主机启动 MainActivity，不使用曾卡住的 ActivityScenario。设备解锁/USB 安装拒绝与模型运行错误应分开记录。

## 语音库修复复现

官方 sherpa-onnx v1.13.8 源码归档 SHA-256 为 `7ee4f2d117bf72a0651146535b9158d62f11a86520e68142270ca9a35a9032b7`，解压目录后缀 `11afbd0`。`tools/prepare-sherpa-utf8-fix.py` 验证并应用补丁；忽略源码中与构建无关的外部符号链接。`tools/build-patched-sherpa.ps1` 使用官方 AAR 内的 ONNX Runtime 1.28.2、对应官方头文件和固定哈希的源码依赖编译 ARM64 JNI，随后 `package-patched-sherpa.py` 生成修复版 AAR。

Whisper 的 BPE token 可能只包含一个汉字的部分 UTF-8 字节，原库先对每个片段删除不完整编码，导致最终文字丢字。修复先拼接原始字节，再对完整转写统一处理；token 元数据仍保持原库的安全处理。另有一个构建补丁给 Windows 空格路径加引号。新 JNI 的 ELF LOAD 对齐为 16 KB。

桌面 ASR 对比仅用于兼容性诊断。原始失败记录保留，不用于声称手机新版识别准确率。`ASR_GGUF_DESKTOP_RESULTS.json` 为同一固定 llama.cpp 提交的替换模型四语结果，手机测试须另行完成。


0.8.0 增加 AppCompat 1.6.1 与仅供开发测试的 Robolectric 4.14.1。首次在新电脑构建需要联网缓存这些依赖及 Android 测试运行库；运行库缓存位于 `.tooling/robolectric`。安装到手机的 App 不含 Robolectric，也未新增联网权限。详见 `UI_LANGUAGE_0.8.0.md`。
