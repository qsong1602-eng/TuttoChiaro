"""Same pinned llama.cpp revision as Android; desktop timings remain separate."""
import json
import subprocess
import time
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
model = ROOT / 'models/qwen_asr'
template = "{{ '<|im_start|>system\\n<|im_end|>\\n' }}" + json.loads(
    (ROOT / '.tooling/downloads/qwen-asr-gguf-metadata.json').read_text(encoding='utf-8-sig'))['tokenizer.chat_template']
results = []
for language in ['zh', 'en', 'es', 'it']:
    command = [str(ROOT / '.tooling/llama-win/llama-mtmd-cli.exe'),
        '-m', str(model / 'Qwen3-ASR-1.7B-Q8_0.gguf'),
        '--mmproj', str(model / 'mmproj-Qwen3-ASR-1.7B-bf16.gguf'),
        '--audio', str(ROOT / f'.tooling/temp/speech-wavs/{language}.wav'),
        '-p', '<__media__>', '--chat-template', template, '--temp', '0',
        '-t', '4', '-tb', '4', '-b', '128', '-ub', '128', '-c', '4096', '-n', '512',
        '--no-mmproj-offload', '-ngl', '0', '--no-warmup']
    start = time.time()
    result = subprocess.run(command, stdin=subprocess.DEVNULL, stdout=subprocess.PIPE, stderr=subprocess.PIPE, timeout=300)
    output = result.stdout.decode('utf-8', errors='replace')
    errors = result.stderr.decode('utf-8', errors='replace')
    (ROOT / f'docs/ASR_GGUF_DESKTOP_{language}.txt').write_text(output + '\n\n' + errors, encoding='utf-8')
    row = dict(language=language, exit=result.returncode, seconds=time.time()-start, output=output)
    results.append(row)
    print(json.dumps(row, ensure_ascii=False), flush=True)
    if result.returncode:
        print(errors[-2000:], flush=True)
        break
(ROOT / 'docs/ASR_GGUF_DESKTOP_RESULTS.json').write_text(json.dumps(results, ensure_ascii=False, indent=2), encoding='utf-8')
assert len(results) == 4 and all(r['exit'] == 0 and r['output'].strip() for r in results), 'GGUF compatibility check failed'
