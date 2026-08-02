# Player-style deploy: stage the mod into the game folder and patch
# SliceAndDice.json so the game's own launcher (bundled OpenJ9 Java 8 JRE)
# loads the agent. Backs up the original json once as SliceAndDice.json.bak.
# NOTE: not yet exercised — the dev loop uses run-dev.ps1. Smoke-test this on
# the bundled JRE before any release.
param(
    [string]$GameDir = 'C:\Program Files (x86)\Steam\steamapps\common\Slice_n_Dice'
)
$ErrorActionPreference = 'Stop'
$root = Split-Path -Parent $PSScriptRoot

& gradle -p $root build
if ($LASTEXITCODE -ne 0) { throw "gradle build failed" }

$modDir = Join-Path $GameDir 'mods\snd-access'
New-Item -ItemType Directory -Force $modDir | Out-Null
Copy-Item (Join-Path $root 'host\build\libs\snd-host-all.jar') $modDir -Force
Copy-Item (Join-Path $root 'module\build\libs\snd-module.jar') $modDir -Force
Copy-Item (Join-Path $root 'third_party\prism\prism.dll') $modDir -Force

$jsonPath = Join-Path $GameDir 'SliceAndDice.json'
$bakPath = "$jsonPath.bak"
if (-not (Test-Path $bakPath)) { Copy-Item $jsonPath $bakPath }

$cfg = Get-Content $bakPath -Raw | ConvertFrom-Json
$cfg.vmArgs = @(
    '-Xmx1G',
    '-javaagent:mods/snd-access/snd-host-all.jar',
    '-Dsnd.module=mods/snd-access/snd-module.jar',
    '-Djna.library.path=mods/snd-access'
)
$cfg | ConvertTo-Json -Depth 5 | Out-File $jsonPath -Encoding utf8
Write-Host "Deployed to $modDir and patched SliceAndDice.json (backup at $bakPath)."
Write-Host "Restore the original launch with: Copy-Item '$bakPath' '$jsonPath' -Force"
