"""Pinned, resumable parallel downloads. No model code is executed."""
import concurrent.futures as cf
import hashlib
import json
import os
import time
import urllib.request as http
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
CHUNK = 8 * 1024 * 1024

def sha(path):
    with path.open('rb') as f:
        return hashlib.file_digest(f, 'sha256').hexdigest()

def specs():
    # Use checked-in provenance, not a tree JSON from a developer's download cache.
    result = json.loads((ROOT / 'models/suite-lock.json').read_text(encoding='utf-8'))
    # The package stager adds HY separately; keep the existing suite-lock format.
    result.insert(0, dict(group='hy', path='Hy-MT2-1.8B-Q4_K_M.gguf',
        local='Hy-MT2-1.8B-Q4_K_M.gguf', repo='tencent/Hy-MT2-1.8B-GGUF',
        revision='main', size=1133080448,
        sha256='dc5f44fcf1fa496ee7ad725982c0c8c553a4de00259b53af84c4b89fb0c06699'))
    for item in result:
        if not item.get('sha256') or len(item['sha256']) != 64:
            raise ValueError(f'Missing SHA-256 in model lock: {item["local"]}')
    return result

def download(s):
    target = ROOT / 'models' / s['local']
    target.parent.mkdir(parents=True, exist_ok=True)
    if target.exists() and target.stat().st_size == s['size'] and (not s['sha256'] or sha(target) == s['sha256']):
        s['sha256'] = sha(target)
        return
    parts = target.with_name(target.name + '.parts')
    parts.mkdir(exist_ok=True)
    old = target.with_name(target.name + '.partial')
    ranges = [(i, min(CHUNK, s['size'] - i)) for i in range(0, s['size'], CHUNK)]
    if old.exists():
        with old.open('rb') as src:
            for offset, size in ranges:
                if offset + size > old.stat().st_size:
                    break
                part = parts / str(offset)
                if not part.exists():
                    src.seek(offset)
                    part.write_bytes(src.read(size))
    # A pinned HF resolve URL redirects to a signed CDN URL. The CDN accepts
    # different ranges on that same URL; resolving each chunk adds substantial latency.
    resolve = f'https://huggingface.co/{s["repo"]}/resolve/{s["revision"]}/{s["path"]}?download=true&n={time.time_ns()}'
    with http.urlopen(http.Request(resolve, headers={'Range': 'bytes=0-0'}), timeout=90) as response:
        direct = response.geturl()
    def fetch(item):
        offset, size = item
        part = parts / str(offset)
        if part.exists() and part.stat().st_size == size:
            return
        for attempt in range(12):
            host = 'huggingface.co' if attempt % 3 != 2 else 'hf-mirror.com'
            url = f'https://{host}/{s["repo"]}/resolve/{s["revision"]}/{s["path"]}?download=true&range={offset}&v={time.time_ns()}'
            if attempt < 2:
                url = direct
            try:
                req = http.Request(url, headers={'Range': f'bytes={offset}-{offset+size-1}', 'User-Agent': 'TuttoChiaro/0.4'})
                with http.urlopen(req, timeout=90) as response:
                    if s['size'] > CHUNK:
                        assert response.status == 206 and response.headers['Content-Range'].startswith(f'bytes {offset}-{offset+size-1}/'), 'Unexpected HTTP range'
                    data = response.read(size + 1)
                    assert len(data) == size, 'Truncated range'
                    part.write_bytes(data)
                    return
            except Exception as e:
                if attempt in (2, 7, 11):
                    print(f'{s["local"]} offset {offset}: retry {attempt+1}: {e}', flush=True)
                time.sleep(min(attempt + 1, 5))
        raise RuntimeError(f'Failed range {s["local"]}:{offset}')
    with cf.ThreadPoolExecutor(max_workers=12) as pool:
        completed = 0
        for _ in pool.map(fetch, ranges):
            completed += 1
            if completed % 16 == 0:
                print(f'{s["local"]}: {completed}/{len(ranges)} parts', flush=True)
    with old.open('wb') as dst:
        for offset, _ in ranges:
            with (parts / str(offset)).open('rb') as src:
                while data := src.read(1024 * 1024):
                    dst.write(data)
    actual = sha(old)
    if s['sha256']:
        assert actual == s['sha256'], f'SHA-256 mismatch: {target}'
    else:
        blob = hashlib.sha1(f'blob {s["size"]}\0'.encode() + old.read_bytes()).hexdigest()
        assert blob == s['gitBlob'], f'Git object mismatch: {target}'
        s['sha256'] = actual
    os.replace(old, target)
    print(f'Verified {s["local"]}: {actual}', flush=True)

if __name__ == '__main__':
    items = specs()
    with cf.ThreadPoolExecutor(max_workers=2) as pool:
        list(pool.map(download, items))
    # HY provenance is above; package-offline-suite.py inserts its manifest entry.
    (ROOT / 'models/suite-lock.json').write_text(json.dumps([s for s in items if s['group'] != 'hy'], indent=2) + '\n', encoding='utf-8')
    print('All offline suite files verified.', flush=True)
