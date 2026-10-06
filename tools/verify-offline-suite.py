"""Verify signatures, asset inventory and reconstructed model hashes across all APKs."""
import argparse
import hashlib
import json
import os
import re
import subprocess
import zipfile
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
parser = argparse.ArgumentParser(description=__doc__)
parser.add_argument('--upgrade-from', type=Path, help='Optional previous APK whose signing certificate must match')
ARGS = parser.parse_args()
OUT = ROOT / 'TuttoChiaro-offline-v0.8.0'
BT = ROOT / '.tooling/android-sdk/build-tools/35.0.0'
ENV = {**os.environ, 'JAVA_HOME': str(ROOT / '.tooling/jdk17/jdk-17.0.20+8')}

def command(*args):
    if str(args[0]).endswith('apksigner.bat'):
        args = (ROOT / '.tooling/jdk17/jdk-17.0.20+8/bin/java.exe', '-jar', BT / 'lib/apksigner.jar', *args[1:])
    return subprocess.check_output([str(x) for x in args], env=ENV, text=True, encoding='utf-8', errors='replace')

archives = {}
inventory = {}
certificates = set()
report = dict(models=[], apks={})
try:
    for file in sorted(OUT.glob('*.apk')):
        signed = command(BT / 'apksigner.bat', 'verify', '--verbose', '--print-certs', file)
        badging = command(BT / 'aapt2.exe', 'dump', 'badging', file)
        assert "versionCode='9'" in badging, f'Wrong version in {file.name}'
        certificate = re.search(r'Signer #1 certificate SHA-256 digest: (\w+)', signed).group(1)
        certificates.add(certificate)
        archive = zipfile.ZipFile(file)
        archives[file.name] = archive
        assert archive.testzip() is None
        for name in archive.namelist():
            if name.startswith('assets/weights/'):
                assert name not in inventory, f'Duplicate asset: {name}'
                inventory[name] = archive
        with file.open('rb') as source:
            digest = hashlib.file_digest(source, 'sha256').hexdigest()
        report['apks'][file.name] = dict(bytes=file.stat().st_size, sha256=digest)
    assert len(certificates) == 1, 'Base and splits must use the same certificate'
    if ARGS.upgrade_from is not None:
        old_signature = command(BT / 'apksigner.bat', 'verify', '--print-certs', ARGS.upgrade_from)
        assert next(iter(certificates)) in old_signature, 'Upgrade signing certificate changed'
    report['upgradeSignatureCheck'] = 'VERIFIED' if ARGS.upgrade_from is not None else 'NOT_REQUESTED'
    permissions = command(BT / 'aapt2.exe', 'dump', 'permissions', OUT / 'base.apk')
    assert 'android.permission.INTERNET' not in permissions
    assert 'android.permission.ACCESS_NETWORK_STATE' not in permissions
    base = archives['base.apk']
    for lib in ['libtutto_llm.so', 'libsherpa-onnx-jni.so', 'libonnxruntime.so']:
        assert f'lib/arm64-v8a/{lib}' in base.namelist()
    manifest = json.loads(base.read('assets/model-manifest.json'))
    expected_assets = set()
    assert set(manifest['groups']) == {'hy', 'qwen', 'qwen_asr', 'whisper'}
    for group, files in manifest['groups'].items():
        for spec in files:
            digest = hashlib.sha256()
            size = 0
            for asset in spec['assets']:
                name = 'assets/' + asset
                expected_assets.add(name)
                with inventory[name].open(name) as source:
                    while data := source.read(4 * 1024 * 1024):
                        digest.update(data)
                        size += len(data)
            assert size == spec['size'] and digest.hexdigest() == spec['sha256'], f'Corrupt model: {group}/{spec["path"]}'
            report['models'].append(dict(group=group, path=spec['path'], bytes=size, sha256=digest.hexdigest()))
            print(f'Verified {group}/{spec["path"]}', flush=True)
    assert expected_assets == set(inventory), 'Unlisted model assets'
    report['internetPermission'] = False
    report['certificateSha256'] = next(iter(certificates))
    report['status'] = 'PACKAGE_VERIFIED_DEVICE_TEST_SEPARATE'
    report['deviceTest'] = 'NOT_RECORDED_BY_PACKAGE_VERIFIER'
    (ROOT / 'docs/BUILD_0.8.0_VERIFICATION.json').write_text(json.dumps(report, indent=2), encoding='utf-8')
finally:
    for archive in archives.values():
        archive.close()
