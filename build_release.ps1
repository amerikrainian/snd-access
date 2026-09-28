# Build the distributable mod zip: the mod jars, prism.dll and its licenses
# under mods\snd-access. The zip root IS the game folder, so the installer (and
# a manual user) extracts it straight into the game dir. The installer then
# patches SliceAndDice.json; a manual user runs deploy.ps1 instead,
# which does both steps from a local build.

Set-StrictMode -Version Latest
$ErrorActionPreference = "Stop"

$root = $PSScriptRoot
$releaseDir = Join-Path $root "releases"
$stageDir = Join-Path $root "build\release-stage"

$propsPath = Join-Path $root "gradle.properties"
$versionLine = (Get-Content $propsPath) | Where-Object { $_ -match '^modVersion=(.+)$' } | Select-Object -First 1
if ($null -eq $versionLine) {
    throw "Could not read modVersion from $propsPath"
}
$version = ($versionLine -split '=', 2)[1].Trim()

$hostJar = Join-Path $root "host\build\libs\snd-host-all.jar"
$moduleJar = Join-Path $root "module\build\libs\snd-module.jar"
$prismDir = Join-Path $root "third_party\prism"
$zipPath = Join-Path $releaseDir "SnDAccess-v$version.zip"

& gradle -p $root build
if ($LASTEXITCODE -ne 0) {
    throw "gradle build failed with exit code $LASTEXITCODE"
}

foreach ($required in @($hostJar, $moduleJar, (Join-Path $prismDir "prism.dll"))) {
    if (-not (Test-Path $required)) {
        throw "Required file not found: $required"
    }
}

if (Test-Path $stageDir) {
    Remove-Item -LiteralPath $stageDir -Recurse -Force
}
$modDir = Join-Path $stageDir "mods\snd-access"
New-Item -ItemType Directory -Force $modDir | Out-Null
New-Item -ItemType Directory -Force $releaseDir | Out-Null

Copy-Item -LiteralPath $hostJar -Destination $modDir
Copy-Item -LiteralPath $moduleJar -Destination $modDir
Copy-Item -LiteralPath (Join-Path $prismDir "prism.dll") -Destination $modDir
# Prism is MPL-2.0 (plus dependency licenses); redistribution ships them beside the dll.
Copy-Item -Path (Join-Path $prismDir "LICENSES") -Destination (Join-Path $modDir "LICENSES") -Recurse

if (Test-Path $zipPath) {
    Remove-Item -LiteralPath $zipPath -Force
}
Compress-Archive -Path (Join-Path $stageDir "*") -DestinationPath $zipPath -Force

Remove-Item -LiteralPath $stageDir -Recurse -Force

Write-Host "Release zip: $zipPath"
