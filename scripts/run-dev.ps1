# Dev launch: build, then run Slice & Dice under the system JDK (21) with the
# agent, dev server, and hot-reloadable module. The player launch path is
# untouched — this never modifies the game install.
param(
    [switch]$NoBuild,
    [int]$Port = 8771
)
$ErrorActionPreference = 'Stop'
$root = Split-Path -Parent $PSScriptRoot
$gameDir = 'C:\Program Files (x86)\Steam\steamapps\common\Slice_n_Dice'

if (-not $NoBuild) {
    & gradle -p $root build
    if ($LASTEXITCODE -ne 0) { throw "gradle build failed" }
}

$hostJar   = Join-Path $root 'host\build\libs\snd-host-all.jar'
$replJar   = Join-Path $root 'devrepl\build\libs\snd-devrepl.jar'
$moduleJar = Join-Path $root 'module\build\libs\snd-module.jar'
$prismDir  = Join-Path $root 'third_party\prism'
foreach ($f in @($hostJar, $replJar, $moduleJar)) {
    if (-not (Test-Path $f)) { throw "missing artifact: $f" }
}

Push-Location $gameDir
try {
    java "-javaagent:$hostJar" -Xmx1G `
        "-Dsnd.dev=1" "-Dsnd.dev.port=$Port" `
        "-Dsnd.module=$moduleJar" `
        "-Djna.library.path=$prismDir" `
        -cp "dice.jar;$replJar" `
        com.tann.dice.desktop.DicetopLauncher
} finally {
    Pop-Location
}
