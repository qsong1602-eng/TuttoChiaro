"""Verify the deliverable contains exact model bytes and the ARM64 inference library."""
import hashlib
import json
import sys
import zipfile
from pathlib import Path

apk = Path(sys.argv[1])
expected = "dc5f44fcf1fa496ee7ad725982c0c8c553a4de00259b53af84c4b89fb0c06699"
with zipfile.ZipFile(apk) as archive:
    model = archive.getinfo("assets/Hy-MT2-1.8B-Q4_K_M.gguf")
    assert model.file_size == 1133080448, "Model size mismatch"
    assert model.compress_type == zipfile.ZIP_STORED, "GGUF must not be compressed"
    with archive.open(model) as contents:
        digest = hashlib.file_digest(contents, "sha256").hexdigest()
    assert digest == expected, "Model SHA-256 mismatch"
    for name in ("assets/HY-MT2-LICENSE.txt", "assets/llama.cpp-LICENSE.txt",
                 "lib/arm64-v8a/libtutto_llm.so"):
        assert archive.getinfo(name).file_size > 0, f"Missing {name}"
    native = [name for name in archive.namelist() if name.startswith("lib/")]
    assert all(name.startswith("lib/arm64-v8a/") for name in native)
with apk.open("rb") as contents:
    apk_hash = hashlib.file_digest(contents, "sha256").hexdigest()
print(json.dumps({"apk": str(apk.resolve()), "size": apk.stat().st_size,
                  "apk_sha256": apk_hash, "model_sha256": digest, "model_stored_uncompressed": True,
                  "native_libraries": native}, indent=2))
