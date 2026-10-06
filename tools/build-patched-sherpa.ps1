$ErrorActionPreference = 'Stop'
$projectDir = Split-Path -Parent $PSScriptRoot
$sourceDir = Join-Path $projectDir '.tooling/sherpa-source/k2-fsa-sherpa-onnx-11afbd0'
$buildDir = Join-Path $projectDir '.tooling/sherpa-build'
$cmakeDir = Join-Path $projectDir '.tooling/android-sdk/cmake/3.22.1/bin'
$ndkDir = Join-Path $projectDir '.tooling/android-sdk/ndk/28.2.13676358'
$env:SHERPA_ONNXRUNTIME_LIB_DIR = Join-Path $projectDir '.tooling/temp/sherpa/jni/arm64-v8a'
$env:SHERPA_ONNXRUNTIME_INCLUDE_DIR = Join-Path $projectDir '.tooling/ort-headers'
$flags = @(
    '-G', 'Ninja', '-S', $sourceDir, '-B', $buildDir,
    "-DCMAKE_MAKE_PROGRAM=$cmakeDir/ninja.exe",
    "-DCMAKE_TOOLCHAIN_FILE=$ndkDir/build/cmake/android.toolchain.cmake",
    '-DCMAKE_BUILD_TYPE=Release', '-DANDROID_ABI=arm64-v8a', '-DANDROID_PLATFORM=android-26',
    '-DANDROID_STL=c++_static', '-DBUILD_SHARED_LIBS=ON',
    '-DCMAKE_SHARED_LINKER_FLAGS=-Wl,-z,max-page-size=16384',
    '-DSHERPA_ONNX_ENABLE_JNI=ON', '-DSHERPA_ONNX_ENABLE_C_API=ON',
    '-DSHERPA_ONNX_ENABLE_BINARY=OFF', '-DSHERPA_ONNX_BUILD_C_API_EXAMPLES=OFF',
    '-DSHERPA_ONNX_ENABLE_TTS=OFF', '-DSHERPA_ONNX_ENABLE_SPEAKER_DIARIZATION=OFF',
    '-DSHERPA_ONNX_ENABLE_PORTAUDIO=OFF', '-DSHERPA_ONNX_ENABLE_WEBSOCKET=OFF',
    '-DSHERPA_ONNX_ENABLE_PYTHON=OFF', '-DSHERPA_ONNX_ENABLE_TESTS=OFF'
)
& "$cmakeDir/cmake.exe" @flags
if ($LASTEXITCODE -ne 0) { throw 'Sherpa configure failed' }
& "$cmakeDir/cmake.exe" --build $buildDir --target sherpa-onnx-jni --parallel 4
if ($LASTEXITCODE -ne 0) { throw 'Sherpa build failed' }
& python -X utf8 (Join-Path $PSScriptRoot 'package-patched-sherpa.py')
if ($LASTEXITCODE -ne 0) { throw 'Patched AAR packaging failed' }
