# Build the standalone installer exe (installer/, Rust + wxWidgets) into
# releases\SnDAccessInstaller.exe.

Set-StrictMode -Version Latest
$ErrorActionPreference = "Stop"

$root = $PSScriptRoot
$installerDir = Join-Path $root "installer"
$releaseDir = Join-Path $root "releases"
$targetExe = Join-Path $installerDir "target\release\snd-access-installer.exe"
$outputExe = Join-Path $releaseDir "SnDAccessInstaller.exe"

if (-not (Test-Path (Join-Path $installerDir "Cargo.toml"))) {
    throw "Installer project not found: $installerDir"
}

. (Join-Path $PSScriptRoot "installer-env.ps1")

Push-Location $installerDir
try {
    cargo build --release
    if ($LASTEXITCODE -ne 0) {
        throw "Installer build failed with exit code $LASTEXITCODE"
    }
}
finally {
    Pop-Location
}

if (-not (Test-Path $targetExe)) {
    throw "Expected installer executable not found: $targetExe"
}

New-Item -ItemType Directory -Force $releaseDir | Out-Null
Copy-Item -LiteralPath $targetExe -Destination $outputExe -Force

Write-Host "Installer: $outputExe"
