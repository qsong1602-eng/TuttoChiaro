$ErrorActionPreference = 'Stop'
$projectDir = Split-Path -Parent $PSScriptRoot
$commit = '7fe450e19305b828c199d602c23a8337aaa1f03b'
$expected = '8BFFE21A3ECCE942656E687EAA78276B8410653D740E8E6F1A3542B309706201'
$archive = Join-Path $projectDir '.tooling/downloads/llama-pinned.zip'
$destination = Join-Path $projectDir '.tooling/llama-pinned'
New-Item -ItemType Directory -Force -Path (Split-Path $archive) | Out-Null
if (!(Test-Path -LiteralPath $archive) -or (Get-FileHash -LiteralPath $archive -Algorithm SHA256).Hash -ne $expected) {
    Invoke-WebRequest -Uri "https://codeload.github.com/ggml-org/llama.cpp/zip/$commit" -OutFile $archive
}
if ((Get-FileHash -LiteralPath $archive -Algorithm SHA256).Hash -ne $expected) {
    throw 'llama.cpp archive SHA-256 mismatch; do not compile an unverified archive.'
}
Expand-Archive -LiteralPath $archive -DestinationPath $destination -Force
Write-Output "llama.cpp v0.5.0 / $commit is ready."
