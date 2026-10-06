# 内置 HY 的 Android 构建

此版本将 Hy-MT2-1.8B Q4_K_M 和 llama.cpp JNI 一起打入 APK，文字翻译只走 HY。ML Kit 仅保留随 APK 分发的语言识别及 Latin OCR，不再使用 ML Kit Translation。最终 APK 的合并 Manifest 必须没有 INTERNET 权限。

## 固定输入

- 模型：`models/Hy-MT2-1.8B-Q4_K_M.gguf`，1,133,080,448 bytes。
- 模型 SHA-256：`dc5f44fcf1fa496ee7ad725982c0c8c553a4de00259b53af84c4b89fb0c06699`。
- llama.cpp：v0.5.0，提交 `7fe450e19305b828c199d602c23a8337aaa1f03b`。源码归档 SHA-256：`8bffe21a3ecce942656e687eaa78276b8410653d740e8e6f1a3542b309706201`。
- Android NDK 28.2.13676358 / r28c，CMake 3.22.1，JDK 17。
- 官方模型源：<https://huggingface.co/tencent/Hy-MT2-1.8B-GGUF>。
- 推理源码：<https://github.com/ggml-org/llama.cpp/tree/7fe450e19305b828c199d602c23a8337aaa1f03b>。
- Apache-2.0 模型许可和 MIT 推理库许可位于 `models/` 并随 APK 分发。

## 构建步骤

1. 将上述 GGUF 放到 `models/`。Gradle 的 `verifyBundledHy` 会验证文件大小和 SHA-256，缺失或损坏即构建失败。
2. 运行 `tools/setup-llama.ps1` 恢复固定版本的官方源码。源码存放在忽略提交的 `.tooling/llama-pinned/`。
3. 在项目 Android SDK 中安装 `ndk;28.2.13676358`、`cmake;3.22.1`。
4. 运行 `tools/build-bundled.ps1 -IncludeDeviceTests`。现有 Maven 缓存充足时离线构建；首次构建仍需取得构建依赖。
5. 产物在 `app/build/outputs/apk/debug/app-debug.apk`。测试 APK 在 `app/build/outputs/apk/androidTest/debug/`。

## 实现边界

模型资产不压缩，避免耗时解压。首次启动从 APK 顺序复制到应用的 `noBackupFilesDir/llm`，以 SHA-256 检查完整性后原子改名。复制失败不产生可用模型；重试会重建临时文件。此处没有网络操作。APK 与展开后的模型会各占一份存储。

当前使用 ARM64 CPU 基线（最多四个线程），尚未做 GPU/NPU 优化。上下文 4096 tokens，每次输出最多 768 tokens；超长输入明确报错，不静默截断。多目标翻译共享模型、顺序生成，并逐份展示。

JNI 使用模型 GGUF 的单轮 Hunyuan Dense 模板；用户正文单独以 `parse_special=false` 编码。每次请求清空上下文，避免上一句内容混入；UTF-8 token 片段按字节合并。预填充分块并在块间检查协程取消；释放模型前等待正在执行的 native 调用结束。

语音输入与朗读仍使用系统离线服务。打包文字 LLM 不会自动补齐 iQOO 11 Pro 缺失的端侧听写服务。中文照片 OCR 尚未包含。

## 验证

`BundledHyTest` 检查应用无联网权限、模型可以本地展开并校验、JNI 能完成四语十二方向的真实生成。该测试验证运行链路，输出样句仍需人工核对语义；不代表所有旅行场景或持续性能已经验收。

iQOO / OriginOS 上运行 instrumentation 时，测试主机需在测试启动后用 ADB 将 `com.tuttochiaro.offline/.MainActivity` 拉到前台，并保持手机解锁。后台测试进程可能停顿，不能将该耗时视为推理延迟。本轮首测 12 方向生成完成，原始输出及语义偏差见 `DEVICE_TEST_PLAN.md`。
