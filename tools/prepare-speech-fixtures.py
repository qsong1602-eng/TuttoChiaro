"""Select one <=20 s public FLEURS test utterance per language, with reference text.
Audio is streamed from the pinned source tar; only selected clips are saved.
FLEURS: https://huggingface.co/datasets/google/fleurs (CC BY 4.0).
"""
import io
import json
import struct
import tarfile
import time
import urllib.request
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
OUT = ROOT / 'app/src/androidTest/assets/speech'
REV = '70bb2e84b976b7e960aa89f1c648e09c59f894dd'
BASE = f'https://huggingface.co/datasets/google/fleurs/resolve/{REV}/data'

def pcm(wav):
    assert wav[:4] == b'RIFF' and wav[8:12] == b'WAVE'
    offset = 12
    config = None
    audio = None
    while offset + 8 <= len(wav):
        name, size = struct.unpack_from('<4sI', wav, offset)
        body = wav[offset + 8:offset + 8 + size]
        if name == b'fmt ':
            config = struct.unpack_from('<HHIIHH', body)
        if name == b'data':
            audio = body
        offset += 8 + size + size % 2
    encoding, channels, rate, _, _, bits = config
    assert channels == 1 and rate == 16000
    if encoding == 1 and bits == 16:
        return audio
    assert encoding == 3 and bits == 32, config
    floats = struct.iter_unpack('<f', audio)
    return b''.join(struct.pack('<h', max(-32768, min(32767, round(x[0]*32768)))) for x in floats)

if __name__ == '__main__':
    OUT.mkdir(parents=True, exist_ok=True)
    results = []
    for language, config in [('zh', 'cmn_hans_cn'), ('en', 'en_us'), ('es', 'es_419'), ('it', 'it_it')]:
        with urllib.request.urlopen(f'{BASE}/{config}/test.tsv', timeout=60) as response:
            rows = [line.split('\t') for line in response.read().decode().splitlines()]
        references = {row[1]: row for row in rows}
        with urllib.request.urlopen(f'{BASE}/{config}/audio/test.tar.gz?download=true&n={time.time_ns()}', timeout=90) as response:
            with tarfile.open(fileobj=response, mode='r|gz') as tar:
                for member in tar:
                    if not member.isfile() or not member.name.endswith('.wav'):
                        continue
                    data = pcm(tar.extractfile(member).read())
                    if not 32000 <= len(data) <= 640000:
                        continue
                    (OUT / f'{language}.pcm').write_bytes(data)
                    row = references[Path(member.name).name]
                    results.append(dict(language=language, config=config, file=member.name,
                        reference=row[2], normalized=row[3], seconds=len(data)/32000,
                        source=f'{BASE}/{config}/audio/test.tar.gz'))
                    print(results[-1], flush=True)
                    break
    (OUT / 'references.json').write_text(json.dumps(results, ensure_ascii=False, indent=2), encoding='utf-8')
    (OUT / 'ATTRIBUTION.txt').write_text('FLEURS, Google, CC BY 4.0\nhttps://huggingface.co/datasets/google/fleurs\n'
        'https://creativecommons.org/licenses/by/4.0/\nSelected test recordings converted to mono 16 kHz PCM16 for engineering smoke tests.\n'
        'One sample per language is not a model quality benchmark.\n', encoding='utf-8')
