"""Keep the official Kotlin API and ORT runtime; replace JNI with our UTF-8 fix."""
import hashlib
import json
import zipfile
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
native = next((ROOT / '.tooling/sherpa-build').rglob('libsherpa-onnx-jni.so'))
original = ROOT / 'app/libs/sherpa-onnx-1.13.8.aar'
patched = ROOT / 'app/libs/sherpa-onnx-1.13.8-utf8fix.aar'
with zipfile.ZipFile(original) as source, zipfile.ZipFile(patched, 'w', compression=zipfile.ZIP_DEFLATED) as target:
    for entry in source.infolist():
        if entry.is_dir():
            continue
        if entry.filename.startswith('jni/') and entry.filename != 'jni/arm64-v8a/libonnxruntime.so':
            continue
        target.writestr(entry.filename, source.read(entry))
    target.write(native, 'jni/arm64-v8a/libsherpa-onnx-jni.so')
def sha(file):
    with file.open('rb') as f:
        return hashlib.file_digest(f, 'sha256').hexdigest()
record = dict(version='1.13.8+whisper-utf8', sourceArchiveSha256='7ee4f2d117bf72a0651146535b9158d62f11a86520e68142270ca9a35a9032b7',
    sourceRevisionPrefix='11afbd0', patch='tools/patches/sherpa-whisper-utf8.patch',
    originalAarSha256=sha(original), patchedAarSha256=sha(patched), jniSha256=sha(native),
    onnxruntime='1.28.2', ndk='28.2.13676358', abi='arm64-v8a')
(ROOT / 'docs/SHERPA_UTF8_FIX_BUILD.json').write_text(json.dumps(record, indent=2), encoding='utf-8')
print(patched)
