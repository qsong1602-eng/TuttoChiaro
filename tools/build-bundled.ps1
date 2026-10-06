param([switch]$IncludeDeviceTests)
$ErrorActionPreference = 'Stop'
$projectDir = Split-Path -Parent $PSScriptRoot
$env:JAVA_HOME = Join-Path $projectDir '.tooling/jdk17/jdk-17.0.20+8'
$env:GRADLE_USER_HOME = Join-Path $projectDir '.tooling/gradle-user-home'
$env:ANDROID_USER_HOME = Join-Path $projectDir '.tooling/android-user-home'
$env:TEMP = Join-Path $projectDir '.tooling/temp'
$env:TMP = $env:TEMP
$env:CMAKE_BUILD_PARALLEL_LEVEL = '4'
$tasks = @(':app:lintDebug', ':app:testDebugUnitTest', ':app:assembleDebug')
if ($IncludeDeviceTests) { $tasks += ':app:assembleDebugAndroidTest' }
Push-Location $projectDir
try {
    & '.\gradlew.bat' @tasks --offline --no-daemon --console=plain --max-workers=1 '-Dorg.gradle.jvmargs=-Xmx3g -Dfile.encoding=UTF-8 -XX:TieredStopAtLevel=1'
    if ($LASTEXITCODE -ne 0) { throw "Build failed with exit code $LASTEXITCODE" }
} finally { Pop-Location }
