# 四语离线听写模型复核 · 2026-09-24

用户要求先调研再决定语音模型，不能仅因为包体和接入便利直接选 Whisper Small。此前关于内置 Whisper Small 的方案撤回为待评测候选；当前 0.3.0 APK 仍没有内置 ASR。本轮准备的运行库、下载中的模型均不代表已集成或已通过手机测试。

## 结论

新一代语音模型已经能在部分公开测试中超过 Whisper large-v3，但“新 LLM 全面胜过 Whisper”不成立。必须区分 Whisper Small、large-v3 与 large-v3-turbo，也必须区分 Qwen3-ASR 的 0.6B 与 1.7B。文字翻译用的 Qwen3.5-4B 不能替代专门的 Qwen3-ASR。

建议质量候选优先看 **Qwen3-ASR-1.7B**，成熟部署对照看 **Whisper large-v3-turbo**，较小候选看 **Qwen3-ASR-0.6B**。最终默认值取决于四语录音和两台手机的实测；不因 0.6B 更新或更小就推断其准确率更高。Whisper Small 只保留为低资源基线。

## 可比较的公开结果

以下取自 Qwen 官方模型卡的统一多语评测表，数值越低越好。报告按语言使用 WER 或 CER，聚合为语言宏平均；不是手机推理速度，也不是本项目量化版本的成绩。

| 多语测试 | Whisper large-v3 | Qwen3-ASR-0.6B | Qwen3-ASR-1.7B |
|---|---:|---:|---:|
| CommonVoice（13 语） | 10.77 | 12.75 | 9.18 |
| FLEURS（12 语） | 5.27 | 7.57 | 4.90 |
| MLC-SLM（11 语） | 15.68 | 15.84 | 12.74 |

这三组都覆盖中英西意或其中相应多语集合，具体而言 CommonVoice 和上述 FLEURS 集合包含所需四语；MLC-SLM 含英西意，不含中文。表格是多语平均，不能据此断言西语、意语各自一定更好。来源为模型作者自测，尚缺本项目独立复核。[Qwen 官方结果](https://huggingface.co/Qwen/Qwen3-ASR-0.6B#evaluation)、[评测口径](https://arxiv.org/html/2601.21337v1)

Qwen3-ASR 两个版本均明确覆盖中文、英语、西班牙语、意大利语，并具备语言识别。官方多语语言识别平均结果为 Whisper large-v3 94.1%、Qwen3-ASR-0.6B 96.8%、1.7B 97.9%；这对自动切换对话语言有参考价值，仍需短句/混语实测。[官方模型卡](https://huggingface.co/Qwen/Qwen3-ASR-0.6B)

## Android 可用性与取舍

| 候选 | 已核实情况 | 本项目定位 |
|---|---|---|
| Qwen3-ASR-1.7B | 较强多语公开表现；社区已有 ONNX / sherpa 格式转换 | 质量优先候选，需要验证具体量化包与 Android 运行库兼容性 |
| Qwen3-ASR-0.6B | sherpa-onnx 官方文档直接提供 INT8 模型与 Android 支持 | 可落地的较小候选；不能宣称全面胜过 Whisper large-v3 |
| Whisper large-v3-turbo | 809M 参数；large-v3 的解码层从 32 减至 4，以少量质量损失换速度 | 比 Small 更值得保留的部署对照；不能套用 large-v3 的表格成绩 |
| Voxtral Mini 4B Realtime | 四语在其 13 语覆盖内，Apache-2.0；模型卡将 ExecuTorch 端侧路线标为未充分测试 | 后续流式候选，当前没有两台手机上的延迟依据 |

[Qwen3-ASR Android 文档](https://k2-fsa.github.io/sherpa/onnx/qwen3-asr/index.html)、[Whisper turbo 模型卡](https://huggingface.co/openai/whisper-large-v3-turbo)、[Voxtral 模型卡](https://huggingface.co/mistralai/Voxtral-Mini-4B-Realtime-2602)

1.7B 转换来源需要单独核验。某 ONNX 转换项目报告，其特定 1.7B INT8 方案存在控制 token 预测问题，分组 INT4 效果不同；该结果不能推广到所有量化器，但说明“INT8 理应比 INT4 好”也不能作为保证。[转换作者记录](https://github.com/andrewleech/qwen3-asr-onnx)、[另一 sherpa 格式转换](https://huggingface.co/thieunv-asilla/sherpa-onnx-qwen3-asr-1.7B-int8)

当前检索的 Parakeet TDT 0.6B v3 覆盖 25 种欧洲语言但没有中文，不能作为唯一四语听写模型。Moonshine streaming medium 的这张模型卡仅声明英语，也不满足统一四语要求。[Parakeet 语言表](https://huggingface.co/nvidia/parakeet-tdt-0.6b-v3)、[Moonshine 模型卡](https://huggingface.co/moonshine-ai/moonshine-streaming-medium)

## 下一步实测标准

用同一组四语录音，分别包含安静房间、街道背景声、5–15 秒短句、数字/地名、停顿、纯静音、说话人轮换和语种切换。比较转写错误、漏词、无声幻觉和语种判断；单独记录录音结束到文本出现的延迟、应用内存与连续使用的温度变化。

两台手机分别测量。论文中服务器吞吐量、流式算法延迟或桌面成绩不能直接当作手机成绩。测试时把听写和翻译分开，避免将听错的词算作翻译模型的错误。界面上的“翻译模型选择”和“语音识别引擎”也应分开标明。
