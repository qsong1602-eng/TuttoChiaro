param([string]$AdbPath = '', [string]$Serial = '')
$ErrorActionPreference = 'Stop'
if (!$AdbPath) {
    $localAdb = Join-Path $PSScriptRoot '../.tooling/android-sdk/platform-tools/adb.exe'
    if (Test-Path -LiteralPath $localAdb) { $AdbPath = (Resolve-Path -LiteralPath $localAdb).Path }
    else { $AdbPath = (Get-Command adb -ErrorAction Stop).Source }
}
$checksums = Get-Content -Raw (Join-Path $PSScriptRoot 'checksums.json') | ConvertFrom-Json
$apks = @()
foreach ($entry in $checksums.PSObject.Properties) {
    $path = Join-Path $PSScriptRoot $entry.Name
    if ((Get-Item -LiteralPath $path).Length -ne $entry.Value.bytes -or
        (Get-FileHash -LiteralPath $path -Algorithm SHA256).Hash.ToLowerInvariant() -ne $entry.Value.sha256) {
        throw "Missing or corrupt installation file: $($entry.Name)"
    }
    $apks += $path
}
$arguments = @()
if ($Serial) { $arguments += @('-s', $Serial) }
else {
    $devices = @(& $AdbPath devices | Where-Object { $_ -match '\sdevice$' })
    if ($devices.Count -ne 1) { throw 'Connect exactly one unlocked phone, or provide -Serial.' }
}
& $AdbPath @arguments install-multiple -r @apks
if ($LASTEXITCODE -ne 0) { throw 'Installation failed. Unlock phone and allow USB installation, then retry.' }
& $AdbPath @arguments shell am start -n com.tuttochiaro.offline/.MainActivity
