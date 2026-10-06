# 端侧模型复核 · 2026-09-23

> 2026-09-24 选型更新：项目要求优先使用离线 LLM。首轮评测模型选为 Hy-MT2-1.8B Q4_K_M；权重已下载到 `models/` 并按官方 SHA-256 校验，但尚未集成、运行或在手机上实测，因此不是最终产品选型。后续仍需以 vivo X300 Pro 的译文质量及可接受的性能为准；ML Kit 是当前 APK 的旧实现和对照/备用方案。

## 首轮评测权重

- 模型：腾讯官方 `Hy-MT2-1.8B-GGUF` 的 `Q4_K_M` 文件。
- 本地文件：`models/Hy-MT2-1.8B-Q4_K_M.gguf`；精确大小 1,133,080,448 bytes。
- SHA-256：`DC5F44FCF1FA496EE7AD725982C0C8C553A4DE00259B53AF84C4B89FB0C06699`，与官方 Hugging Face 文件页公布值一致。下载使用镜像传输，完整性以官方哈希核对。
- 许可：Apache-2.0；[官方模型文件与许可](https://huggingface.co/tencent/Hy-MT2-1.8B-GGUF)。
- 选择原因：专用于翻译，官方语言表覆盖项目所需的中文、英语、西班牙语和意大利语；Q4_K_M 是首轮质量基线，避免先以最小文件体积决定模型。腾讯官方列出的文件大小约 1.13 GB。
- 状态边界：模型权重不在 APK 内；当前 APK 仍使用 ML Kit。还需接入 Android 推理运行时并在 iQOO 11 Pro、vivo X300 Pro 分别测试质量、时延、峰值内存和持续温升。语音识别、TTS、OCR 仍是独立环节。

设备范围：iQOO 11 Pro 用于测试，vivo X300 Pro（天玑 9500）用于实际使用。内存容量、Android 和 OriginOS 版本在接机后读取；不能把测试机的性能和语音包情况直接套用到实际使用设备。

这是截至本次查询的候选复核，并非“全部在上次开发后发布”的清单。项目旧产物日期为 2026-08-13；下列许多模型在此前已经发布。搜索结果的抓取/更新日期不等于模型发布日期。本轮没有进行模型下载、集成或设备性能测试。

## 优先顺序

建议先对比 **Hy-MT2-1.8B 的常规 4-bit 与 1.25-bit 版本**，再比较 TranslateGemma 4B。语音端优先评估 **Qwen3-ASR-0.6B + sherpa-onnx**，降低对系统听写包的依赖。这是基于任务匹配和部署成本的工程判断，不是已有实测排名。0.2.0 继续使用 ML Kit，作为可回退的基线。

| 候选 | 已核实信息 | 对本项目的判断 |
|---|---|---|
| Hy-MT2-1.8B | 2026-05-21 发布；官方语言表含中、英、西、意；有 GGUF 与极低位量化；官方称 1.25-bit 权重约 440 MB | 首先评测。440 MB 是权重存储量，不是应用总内存；对比 4-bit 检查量化质量损失。 [官方仓库](https://github.com/Tencent-Hunyuan/Hy-MT2) |
| TranslateGemma 4B | 专门用于翻译，55 种语言；专用模板要求源/目标语种字段；输入上下文 2K tokens | 作为较大专用翻译候选；不能直接套通用聊天模板；遵循 Gemma 条款。 [模型卡](https://huggingface.co/google/translategemma-4b-it) |
| Gemma 4 E2B / E4B | 移动部署定位，有官方 QAT 移动权重；LiteRT-LM 路径。官方表中 Mobile text-only 静态加载估计分别约 0.84 / 2.2 GB，不含完整运行时与上下文开销 | 有吸引力的通用对照；E 代表有效参数，不能直接用“2B×位宽”计算占用。 [部署与内存说明](https://ai.google.dev/gemma/docs/core) |
| Qwen3.5 2B / 4B | 模型系列支持 201 种语言和方言；2B 官方模型提供开放权重 | 可作通用模型对照，四语旅行翻译质量需单独测；避免长思考拖慢交互。 [2B 模型卡](https://huggingface.co/Qwen/Qwen3.5-2B) |
| Qwen3-ASR 0.6B | 多语听写和语言识别；Apache-2.0；sherpa-onnx 有 Qwen3-ASR 支持和 Android 示例路径 | 属于语音转文字模型，不能替代文字翻译和 TTS；先测 5–15 秒四语录音。 [模型卡](https://huggingface.co/Qwen/Qwen3-ASR-0.6B)、[部署文档](https://k2-fsa.github.io/sherpa/onnx/qwen3-asr/index.html)、[Android 示例清单](https://github.com/k2-fsa/sherpa-onnx/blob/master/flutter/sherpa_onnx/example/example.md) |

Hy-MT2-1.8B 的模型卡标明 Apache-2.0；Gemma 4 也使用 Apache-2.0。TranslateGemma 的 Gemma 许可不能与 Gemma 4 混同。接入时固定模型修订和推理库版本，并随分发保留对应许可文件。 [Hy-MT2 模型卡](https://huggingface.co/tencent/Hy-MT2-1.8B)、[Gemma 4 模型卡](https://ai.google.dev/gemma/docs/core/model_card_4)

## Android 接入约束

GGUF 路线可参考 llama.cpp 官方 Android binding 和 NDK 交叉编译文档，采用 ARM64 通用基线与运行时能力检测。不要把为某颗芯片编译的全局指令集优化直接用于另一台手机。 [llama.cpp Android 文档](https://github.com/ggml-org/llama.cpp/blob/master/docs/android.md)

Gemma 移动路线可用 LiteRT-LM 的 Kotlin API。库声明支持 GPU/NPU 不代表两台手机的全部模型都能用对应加速器；必须记录实际 backend，并保留 CPU 验证路径。 [LiteRT-LM 官方仓库](https://github.com/google-ai-edge/LiteRT-LM)

本项目接入大模型时建议采用独立引擎选择和显式资源准备。权重应支持用户从文件导入、校验散列后复制到应用目录；禁止翻译途中触发下载。大模型多目标译文先按顺序生成，避免复制三份权重和缓存。以上是后续设计，当前版本未实现。

## 比较方法

使用同一批四语旅行样句，覆盖全部 12 个方向，分别评判语义、数字/金额/时刻、否定、过敏原、专有名词、是否添加原文没有的信息。语义由人工核对，不用“回译看起来通顺”代替验收。

两台设备各自记录冷启动、首字时间、完整译文时间、三个目标的总耗时、峰值 PSS、连续使用十分钟的温度/耗电变化。固定上下文和输出上限、同样的输入、记录量化版本；把厂商服务器吞吐量与手机结果分开。

若小模型在数字、否定或目标语种上退化，则保留现有引擎；若系统缺少四语离线听写，则优先实现自带 ASR。大型多模态模型暂不替代已经打包的路牌 OCR。验收模板见 `DEVICE_TEST_PLAN.md`。
