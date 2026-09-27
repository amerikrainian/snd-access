# Run the installer's unit tests (cargo test in installer/).

Set-StrictMode -Version Latest
$ErrorActionPreference = "Stop"

$root = $PSScriptRoot
$installerDir = Join-Path $root "installer"

if (-not (Test-Path (Join-Path $installerDir "Cargo.toml"))) {
    throw "Installer project not found: $installerDir"
}

. (Join-Path $PSScriptRoot "installer-env.ps1")

Push-Location $installerDir
try {
    cargo test
    if ($LASTEXITCODE -ne 0) {
        throw "Installer tests failed with exit code $LASTEXITCODE"
    }
}
finally {
    Pop-Location
}
