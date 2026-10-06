# TuttoChiaro 离线旅行翻译

Android 端侧多语言翻译（默认常用语言为中文、英语、西班牙语、意大利语），支持多目标译文、语音输入、路牌 OCR 和逐条离线朗读。测试设备 iQOO 11 Pro；实际使用目标 vivo X300 Pro（天玑 9500），两台设备须分别验收。

当前代码为 **0.8.0 界面语言选择版**，实现和验证范围见 [界面语言说明](docs/UI_LANGUAGE_0.8.0.md)。不能把旧版测试结果当作新版验收。连续听译与独立语音模型的分阶段安排见 [开发计划和调研](docs/LIVE_SPEECH_PLAN_2026-09-26.md)。

## Source repository and first-time setup

This repository contains the Android source, build tools, model provenance and licenses. **Model weights and model-containing APKs are not included.** Download and package them locally before building the complete offline app. No API key is required for translation or speech recognition; the installed app has no Internet permission.

Follow [the fresh-clone setup guide](docs/SETUP.md) for the exact model filenames, download/checksum instructions, Android SDK and JDK configuration, and first online build. `models/suite-lock.json` records model sources and SHA-256 checksums; `tools/download_offline_suite.py` restores the required weights, including HY, Qwen translation, Qwen ASR and Whisper.

`.gitignore` excludes credentials, signing keys, `.env` files, `local.properties`, toolchain caches, model weights (including nested files and download fragments), generated installers, private handoff notes and raw device diagnostics. Keep personal files in `private/` and credentials in `.env` or `secrets/`, which are ignored. Do not embed secrets in source files or force-add ignored files. The checked-in patched sherpa-onnx AAR is an inference runtime, without model weights; its provenance is documented in [the setup guide](docs/SETUP.md).

Historical documents may refer to local verification reports or device logs. Those generated/private records are deliberately omitted from this repository; they are not needed to build it.

| 环节 | 当前端侧方案 |
|---|---|
| 文字翻译 | HY-MT2-1.8B Q4_K_M / Qwen3.5-4B Q4_K_M，设置中二选一 |
| 语音识别 | Qwen3-ASR-1.7B Q8_0 + BF16 音频编码器 / Whisper large-v3-turbo INT8，设置中二选一 |
| 文字语言识别 | 随应用提供的 ML Kit Language ID |
| 路牌 OCR | 随应用提供的 ML Kit Latin OCR |
| 朗读 | 手机已安装的离线 TTS 音色 |

模型随完整离线安装套件提供，应用没有联网权限。首次选择模型时在手机本地展开并校验，不依赖厂商端侧语音识别服务。翻译与语音模型依次运行、释放内存，选择会自动保存。新版本尚未使用 GPU/NPU 加速。

## 构建与安装

首次克隆请先完成 [本地环境与模型配置](docs/SETUP.md)。以下命令用于已配置好环境、已缓存依赖的电脑。详见 [离线套件构建说明](docs/OFFLINE_SUITE_BUILD.md)。固定依赖为 Android SDK 35、NDK r28c、CMake 3.22.1、JDK 17、llama.cpp v0.5.0、sherpa-onnx 1.13.8。

```powershell
python -X utf8 tools/download_offline_suite.py
python -X utf8 tools/package-offline-suite.py stage
./tools/build-bundled.ps1 -IncludeDeviceTests
python -X utf8 tools/package-offline-suite.py package
python -X utf8 tools/verify-offline-suite.py
./TuttoChiaro-offline-v0.8.0/install.ps1
```

所有模型约 7.7 GB，使用同一应用的基础 APK + 模型 split APK。**必须一次安装全部 APK，不能只打开 base.apk。** 安装脚本在电脑端经 USB 完成安装，保留已有数据；手机需要解锁并允许安装确认。建议预留至少 18 GB 空间。套件包含校验清单及模型、推理库许可证。

## 使用

- 首页：点击标签或左右滑动切换“普通翻译 / 连续听译”。普通文字和连续听译记录分别保留，切页停止当前收音和未完成任务。
- 文字：在“普通翻译”页自动判断或手选输入语言，再选目标并输入文字，多个译文依次显示。
- 语音：选择识别模型，点“说话”，说完点“完成”，随后识别并自动翻译。每段最长 20 秒，录音只放在内存。识别中点“取消识别”可丢弃结果；原生计算可能需稍候才能释放。
- 连续听译：进入“连续听译”标签，点“开始连续听译”。停顿后按段显示原文和译文，收音继续进行；每段最多 8 秒、每轮最多 2 分钟。点“完成收音”处理尾段，取消则停止未完成任务。处理积压时自动停止收音并提示。当前是有延迟的逐段识别与翻译，尚非逐字实时字幕；固定能量门限在弱声和噪声下需继续测试。
- OCR：拍摄前可选窄框/标准框/大框和调整变焦；只裁剪并识别框内像素。拍后先显示裁剪照片及可修改文字，确认后翻译到已选目标。设备开放长焦时提供镜头选项，否则提示系统变焦可能包含数码裁切。详见 [相机实现与验收](docs/OCR_CAMERA_0.7.0.md) 和 [0.7.1 旋转错位修复与真机检查](docs/OCR_CAMERA_0.7.1.md)。
- 朗读：点击译文的小喇叭。设置页可为常用语言选择、试听离线系统音色，调整 0.75–1.2 倍语速。默认 1.0 倍，音调保持自然值；尚未内置新的 TTS 大模型。听译期间禁止播放，避免回录。
- 设置：统一选择翻译/语音模型、准备本地资源和调整朗读。
- 界面语言：设置顶部可选跟随系统、中文、English、Español、Italiano，与翻译语言独立。详见 [实现与验证](docs/UI_LANGUAGE_0.8.0.md)。本轮暂无手机连接，设备安装和视觉验收待完成。
- 常用语言：设置中至少保留两种，首页只显示所选语言供再次选择。新增法、德、葡、日、韩、俄、泰、越南语，共 12 种。新增语言接线已完成，手机语音与翻译质量尚未逐一实测，详见 [语言范围和验收](docs/UI_LANGUAGES_0.6.0.md)。

## 验证与边界

- 静态检查、JVM 测试、离线套件完整性校验和手机运行测试分别记录，不能互相替代。
- 新 ASR 的工程测试包含四种语言各一条带参考转写的公开 FLEURS 录音（CC BY 4.0）。这不是完整准确率评测；仍需旅行口音、噪声和连续使用测试。
- 文字输入上限 1200 字符，输出上限 768 tokens，超限明确报错。Qwen3.5 与 HY 的译文仍需核对数字、方向、否定词等关键信息。
- Qwen3-ASR 采用 ggml-org 的 Q8 权重和 BF16 音频编码器；Whisper 使用修复中文编码拼接的本地库。来源、哈希与补丁随项目记录，模型榜单不能代替手机实测。
- 当前 OCR 只含 Latin 模型；中文拍照 OCR 尚未集成。朗读音色质量由手机 TTS 引擎决定。
- 仅在用户点击时申请麦克风或相机权限；不申请网络、定位、通讯录、存储或后台录音权限。安装系统 TTS 音色的外部界面可能自行联网。

历史 HY 单 APK 构建说明见 [0.3.0 构建说明](docs/BUNDLED_HY_BUILD.md)。私人设备测试记录仅保存在本地。

