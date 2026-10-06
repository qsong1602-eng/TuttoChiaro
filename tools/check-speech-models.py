"""Desktop compatibility check only. Timings do not represent Android performance."""
import argparse
import json
import subprocess
import time
import wave
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
parser = argparse.ArgumentParser()
parser.add_argument('model', choices=['qwen_asr', 'whisper'])
args = parser.parse_args()
fixtures = ROOT / 'app/src/androidTest/assets/speech'
temporary = ROOT / '.tooling/temp/speech-wavs'
temporary.mkdir(parents=True, exist_ok=True)
waves = []
for lang in ['zh', 'en', 'es', 'it']:
    file = temporary / f'{lang}.wav'
    with wave.open(str(file), 'wb') as output:
        output.setparams((1, 2, 16000, 0, 'NONE', 'not compressed'))
        output.writeframes((fixtures / f'{lang}.pcm').read_bytes())
    waves.append(str(file))
binary = next((ROOT / '.tooling/sherpa-win').rglob('sherpa-onnx-offline.exe'))
model = ROOT / 'models' / args.model
if args.model == 'qwen_asr':
    options = [f'--qwen3-asr-conv-frontend={model / "conv_frontend.onnx"}',
        f'--qwen3-asr-encoder={model / "encoder.int8.onnx"}',
        f'--qwen3-asr-decoder={model / "decoder.int8.onnx"}',
        f'--qwen3-asr-tokenizer={model / "tokenizer"}']
    required = ['conv_frontend.onnx', 'encoder.int8.onnx', 'decoder.int8.onnx', 'tokenizer/vocab.json', 'tokenizer/merges.txt']
else:
    options = [f'--whisper-encoder={model / "turbo-encoder.int8.onnx"}',
        f'--whisper-decoder={model / "turbo-decoder.int8.onnx"}',
        f'--tokens={model / "turbo-tokens.txt"}', '--whisper-task=transcribe', '--whisper-tail-paddings=1000']
    required = ['turbo-encoder.int8.onnx', 'turbo-decoder.int8.onnx', 'turbo-tokens.txt']
assert all((model / name).is_file() for name in required), 'Wait for all verified model files'
start = time.time()
result = subprocess.run([str(binary), '--num-threads=4', '--debug=false', *options, *waves],
    stdout=subprocess.PIPE, stderr=subprocess.STDOUT, timeout=600)
output = result.stdout.decode('utf-8', errors='replace')
report = ROOT / 'docs' / f'ASR_DESKTOP_{args.model}.txt'
report.write_text('DESKTOP COMPATIBILITY CHECK, NOT ANDROID TIMINGS\n' + output +
    f'\nexit={result.returncode}, wallSeconds={time.time()-start:.2f}\n', encoding='utf-8')
print(output[-9000:])
print(f'Exit: {result.returncode}. Report: {report}')
raise SystemExit(result.returncode)
