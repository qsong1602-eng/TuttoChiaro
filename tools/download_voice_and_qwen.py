"""Download public, pinned model files; publish only after SHA-256 verification."""
import concurrent.futures
import hashlib
import os
import time
import urllib.request
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
FILES = [
    ("unsloth/Qwen3.5-4B-GGUF", "e87f176479d0855a907a41277aca2f8ee7a09523", "Qwen3.5-4B-Q4_K_M.gguf", "models/Qwen3.5-4B-Q4_K_M.gguf", 2740937888, "00fe7986ff5f6b463e62455821146049db6f9313603938a70800d1fb69ef11a4"),
    ("csukuangfj/sherpa-onnx-whisper-small", "8f3c18b358db4d1f2fc1eae49d75cd20989e4309", "small-encoder.int8.onnx", "models/whisper-small/small-encoder.int8.onnx", 112442483, "4cbe7b22fa9026b843b60a68640c747de05bafb1a11b57edc0e66c232d9f33a9"),
    ("csukuangfj/sherpa-onnx-whisper-small", "8f3c18b358db4d1f2fc1eae49d75cd20989e4309", "small-decoder.int8.onnx", "models/whisper-small/small-decoder.int8.onnx", 262226114, "acad50b5c782696e91b55914cc5ab4f756f1532f76e22aa6fc615f39fb69a8ee"),
]

def digest(path):
    with path.open("rb") as source:
        return hashlib.file_digest(source, "sha256").hexdigest()

def download(spec):
    repo, revision, name, destination, size, sha = spec
    target = ROOT / destination
    target.parent.mkdir(parents=True, exist_ok=True)
    if target.exists() and target.stat().st_size == size and digest(target) == sha:
        print(f"Verified existing {name}", flush=True)
        return
    partial = target.with_suffix(target.suffix + ".partial")
    for attempt in range(6):
        host = "huggingface.co" if attempt < 2 else "hf-mirror.com"
        offset = partial.stat().st_size if partial.exists() else 0
        if offset == size:
            if digest(partial) != sha:
                partial.unlink()
                raise RuntimeError(f"Checksum mismatch for {name}")
            os.replace(partial, target)
            return
        headers = {"User-Agent": "TuttoChiaro-model-preparation/0.4"}
        if offset:
            headers["Range"] = f"bytes={offset}-"
        url = f"https://{host}/{repo}/resolve/{revision}/{name}?download=true&nonce={time.time_ns()}"
        try:
            with urllib.request.urlopen(urllib.request.Request(url, headers=headers), timeout=60) as response:
                append = response.status == 206 and offset > 0
                if append and not response.headers.get("Content-Range", "").startswith(f"bytes {offset}-"):
                    raise RuntimeError("Unexpected resume range")
                if not append:
                    offset = 0
                with partial.open("ab" if append else "wb") as output:
                    last = -1
                    while block := response.read(4 * 1024 * 1024):
                        output.write(block)
                        offset += len(block)
                        if offset > size:
                            raise RuntimeError("Download exceeds pinned size")
                        percent = offset * 10 // size
                        if percent != last:
                            last = percent
                            print(f"{name}: {offset}/{size} bytes", flush=True)
            if partial.stat().st_size == size and digest(partial) == sha:
                os.replace(partial, target)
                print(f"Verified {name}", flush=True)
                return
        except Exception as error:
            print(f"{name}: attempt {attempt + 1}: {type(error).__name__}: {error}", flush=True)
    raise RuntimeError(f"Could not download and verify {name}")

if __name__ == "__main__":
    with concurrent.futures.ThreadPoolExecutor(max_workers=3) as pool:
        for result in pool.map(download, FILES):
            pass
