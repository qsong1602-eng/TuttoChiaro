from pathlib import Path
import tarfile,difflib,hashlib
root=Path(__file__).resolve().parents[1]
assert hashlib.sha256((root/".tooling/downloads/sherpa-source.tar.gz").read_bytes()).hexdigest() == "7ee4f2d117bf72a0651146535b9158d62f11a86520e68142270ca9a35a9032b7"
with tarfile.open(root/'.tooling/downloads/sherpa-source.tar.gz') as t:
 t.extractall(root/'.tooling/sherpa-source',members=(m for m in t if m.isfile() or m.isdir()),filter='data')
src=next((root/'.tooling/sherpa-source').iterdir())
p=src/'sherpa-onnx/csrc/offline-recognizer-whisper-impl.h'
a=p.read_text(encoding='utf-8');b=a.replace('      text += s;', '      // BPE pieces may split a UTF-8 codepoint. Normalize only after joining.\n      text += sym_table[i];',1).replace('    r.text = text;', '    r.text = ApplyHomophoneReplacer(ApplyInverseTextNormalization(text));',1).replace('            seg_text += s;', '            seg_text += sym_table[tok_id];').replace('r.segment_texts.push_back(std::move(seg_text));','r.segment_texts.push_back(ApplyHomophoneReplacer(ApplyInverseTextNormalization(seg_text)));')
assert a!=b
p.write_text(b,encoding='utf-8')
patch=root/'tools/patches/sherpa-whisper-utf8.patch';patch.parent.mkdir(parents=True,exist_ok=True)
patch.write_text(''.join(difflib.unified_diff(a.splitlines(True),b.splitlines(True),fromfile='a/sherpa-onnx/csrc/offline-recognizer-whisper-impl.h',tofile='b/sherpa-onnx/csrc/offline-recognizer-whisper-impl.h')),encoding='utf-8')
print(src)

# Quote the linker script path because the project directory contains a space.
p=src/'sherpa-onnx/jni/CMakeLists.txt'
a=p.read_text()
b=a.replace('LINK_FLAGS "-Wl,--version-script=${CMAKE_CURRENT_SOURCE_DIR}/sherpa-onnx-symbols.lds"', 'LINK_FLAGS "-Wl,--version-script=\\"${CMAKE_CURRENT_SOURCE_DIR}/sherpa-onnx-symbols.lds\\""')
p.write_text(b)
