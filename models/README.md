# Local models (weights are not stored in Git)

Run `python -X utf8 tools/download_offline_suite.py` from the project root to restore all required HY, Qwen translation, Qwen ASR and Whisper files. See [the setup guide](../docs/SETUP.md) for exact paths, toolchain configuration and packaging instructions. The downloader reads Qwen/Whisper provenance from the checked-in `suite-lock.json`; HY's size and checksum are pinned in the downloader and package stager.

Only this README, `suite-lock.json` and reviewed license texts are committed from this directory. All subdirectories, weights, vocabularies and incomplete downloads remain local. The app uses these files after packaging them into local model split APKs; it does not fetch models on the phone.

## Hy-MT2-1.8B Q4_K_M

- 文件：`Hy-MT2-1.8B-Q4_K_M.gguf`
- 来源：腾讯官方 [Hy-MT2-1.8B-GGUF 仓库](https://huggingface.co/tencent/Hy-MT2-1.8B-GGUF)
- 传输：Hugging Face 镜像；下载后与官方文件页公布的 SHA-256 完全一致。
- 文件大小：1,133,080,448 bytes
- SHA-256：`DC5F44FCF1FA496EE7AD725982C0C8C553A4DE00259B53AF84C4B89FB0C06699`
- 许可：Apache-2.0，见上方官方仓库的 `LICENSE.txt`。

0.3.0 按用户要求将此模型内置到 APK，并通过 llama.cpp JNI 执行文字翻译；0.2.0 及更早版本不包含它。模型与引擎许可文件也在本目录，随 APK 分发。权重完整性在构建时及手机首次本地准备时检查。

所有模型权重及下载片段均被 `.gitignore` 排除。
