"""Stage the base manifest, then build signed asset splits (< 1 GiB each).

Usage: python tools/package-offline-suite.py stage
       tools/build-bundled.ps1 -IncludeDeviceTests
       python tools/package-offline-suite.py package
"""
import hashlib
import json
import os
import shutil
import subprocess
import sys
import zipfile
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
STAGE = ROOT / 'build/suite-assets'
VERSION_NAME = '0.8.0'
VERSION_CODE = 9
OUT = ROOT / f'TuttoChiaro-offline-v{VERSION_NAME}'
BUILD_TOOLS = ROOT / '.tooling/android-sdk/build-tools/35.0.0'
CAPACITY = 1024 ** 3

def sha(path):
    with path.open('rb') as f:
        return hashlib.file_digest(f, 'sha256').hexdigest()

def stage():
    files = json.loads((ROOT / 'models/suite-lock.json').read_text(encoding='utf-8'))
    files.insert(0, dict(group='hy', path='Hy-MT2-1.8B-Q4_K_M.gguf', local='Hy-MT2-1.8B-Q4_K_M.gguf',
        size=1133080448, sha256='dc5f44fcf1fa496ee7ad725982c0c8c553a4de00259b53af84c4b89fb0c06699'))
    groups, packs = {}, [[]]
    used = 0
    for spec in files:
        file = ROOT / 'models' / spec['local']
        assert file.stat().st_size == spec['size'] and sha(file) == spec['sha256'], f'Unverified: {file}'
        entry = dict(path=spec['path'], size=spec['size'], sha256=spec['sha256'], assets=[])
        offset, number = 0, 0
        while offset < spec['size']:
            if used == CAPACITY:
                packs.append([])
                used = 0
            count = min(spec['size'] - offset, CAPACITY - used)
            asset = f'weights/{spec["group"]}/{spec["path"]}.{number}.chunk'
            entry['assets'].append(asset)
            packs[-1].append(dict(source=spec['local'], offset=offset, size=count, asset=asset))
            used += count
            offset += count
            number += 1
        groups.setdefault(spec['group'], []).append(entry)
    base = STAGE / 'base'
    base.mkdir(parents=True, exist_ok=True)
    (base / 'model-manifest.json').write_text(json.dumps(dict(version=VERSION_CODE, groups=groups), indent=2), encoding='utf-8')
    licenses = base / 'licenses'
    licenses.mkdir(exist_ok=True)
    for license in (ROOT / 'models').glob('*LICENSE*'):
        shutil.copy2(license, licenses / license.name)
    patched = ROOT / 'docs/SHERPA_UTF8_FIX_BUILD.json'
    if patched.exists():
        shutil.copy2(patched, licenses / 'sherpa-local-changes.json')
        for patch in (ROOT / 'tools/patches').glob('sherpa-*.patch'):
            shutil.copy2(patch, licenses / patch.name)
    shutil.copy2(ROOT / 'models/suite-lock.json', base / 'model-sources.json')
    (STAGE / 'layout.json').write_text(json.dumps(packs, indent=2), encoding='utf-8')
    print(f'Staged {len(files)} verified files across {len(packs)} asset splits.')

def run(*args):
    if str(args[0]).endswith('apksigner.bat'):
        args = (ROOT / '.tooling/jdk17/jdk-17.0.20+8/bin/java.exe', '-jar', BUILD_TOOLS / 'lib/apksigner.jar', *args[1:])
    subprocess.run([str(x) for x in args], check=True, env={**os.environ,
        'JAVA_HOME': str(ROOT / '.tooling/jdk17/jdk-17.0.20+8')})

def package():
    OUT.mkdir(exist_ok=True)
    base = ROOT / 'app/build/outputs/apk/debug/app-debug.apk'
    with zipfile.ZipFile(base) as archive:
        assert archive.read('assets/model-manifest.json') == (STAGE / 'base/model-manifest.json').read_bytes(), 'Rebuild base after staging'
    shutil.copy2(base, OUT / 'base.apk')
    packs = json.loads((STAGE / 'layout.json').read_text(encoding='utf-8'))
    for index, pack in enumerate(packs):
        name = f'models_{index:02d}'
        manifest = STAGE / f'{name}.xml'
        manifest.write_text(f'''<manifest xmlns:android="http://schemas.android.com/apk/res/android"
            package="com.tuttochiaro.offline" split="{name}" android:versionCode="{VERSION_CODE}" android:isFeatureSplit="true">
            <uses-sdk android:minSdkVersion="26" android:targetSdkVersion="35"/>
            <application android:hasCode="false"/>
            </manifest>''', encoding='utf-8')
        unsigned = STAGE / f'{name}-unsigned.apk'
        aligned = STAGE / f'{name}-aligned.apk'
        run(BUILD_TOOLS / 'aapt2.exe', 'link', '--manifest', manifest,
            '-I', ROOT / '.tooling/android-sdk/platforms/android-35/android.jar', '-o', unsigned)
        with zipfile.ZipFile(unsigned, 'a', compression=zipfile.ZIP_STORED, allowZip64=False) as archive:
            for piece in pack:
                with (ROOT / 'models' / piece['source']).open('rb') as source:
                    source.seek(piece['offset'])
                    with archive.open('assets/' + piece['asset'], 'w') as target:
                        left = piece['size']
                        while left:
                            data = source.read(min(1024 * 1024, left))
                            assert data, 'Unexpected end of source'
                            target.write(data)
                            left -= len(data)
        run(BUILD_TOOLS / 'zipalign.exe', '-f', '4', unsigned, aligned)
        run(BUILD_TOOLS / 'apksigner.bat', 'sign', '--ks', ROOT / '.tooling/android-user-home/debug.keystore',
            '--ks-key-alias', 'androiddebugkey', '--ks-pass', 'pass:android', '--key-pass', 'pass:android',
            '--v4-signing-enabled', 'false', '--out', OUT / f'{name}.apk', aligned)
        run(BUILD_TOOLS / 'apksigner.bat', 'verify', '--verbose', OUT / f'{name}.apk')
        unsigned.unlink()
        aligned.unlink()
        print(f'Built {name}', flush=True)
    checksums = {p.name: dict(bytes=p.stat().st_size, sha256=sha(p)) for p in sorted(OUT.glob('*.apk'))}
    (OUT / 'checksums.json').write_text(json.dumps(checksums, indent=2), encoding='utf-8')
    shutil.copy2(STAGE / 'base/model-manifest.json', OUT / 'model-manifest.json')
    shutil.copy2(ROOT / 'models/suite-lock.json', OUT / 'model-sources.json')
    shutil.copytree(STAGE / 'base/licenses', OUT / 'licenses', dirs_exist_ok=True)
    shutil.copy2(ROOT / 'tools/install-offline-suite.ps1', OUT / 'install.ps1')
    (OUT / 'README.txt').write_text(f'TuttoChiaro {VERSION_NAME} offline ARM64 suite\n\n'
        'Install all APK files together; do not install base.apk alone.\n'
        'Windows USB install: powershell -ExecutionPolicy Bypass -File install.ps1 -AdbPath <path-to-adb.exe>\n'
        'Unlock the phone and allow its USB installation prompt. Existing app data is preserved.\n'
        'Models: HY-MT2 1.8B Q4_K_M, Qwen3.5 4B Q4_K_M, Qwen3-ASR 1.7B Q8_0 + BF16 audio encoder, Whisper large-v3-turbo INT8.\n'
        'All models are included. First use extracts and verifies files locally. No Internet permission.\n'
        'Allow approximately 18 GB free storage for installation and local model preparation.\n'
        'Home: swipe between ordinary translation and continuous interpretation. Configure your common languages in Settings.\n'
        'Speech input: tap microphone, speak up to 20 seconds, tap Finish. Tap Cancel while recognizing to discard.\n'
        'Continuous mode (experimental): phrase segmentation, up to 120 seconds, bounded queue stops capture if inference falls behind. This is not token-level real-time ASR.\n'
        'Settings provides model selection, favorite languages, voice preview and playback rate. No new TTS model is bundled.\n'
        'TTS uses the phone\'s separately installed offline voices.\n'
        'This is a debug-signed testing build. See project docs for measured validation and limitations.\n', encoding='utf-8')
    print(f'Offline suite: {OUT}')

if __name__ == '__main__':
    {'stage': stage, 'package': package}[sys.argv[1]]()
