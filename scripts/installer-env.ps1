# Shared environment probing for the installer's cargo builds. The wxWidgets
# build uses bindgen (needs libclang) and drives CMake with the Ninja generator;
# Visual Studio ships a ninja.exe that is not on PATH by default.

if ([string]::IsNullOrWhiteSpace($env:LIBCLANG_PATH)) {
    $libclangCandidates = @(
        "C:\Program Files\LLVM\bin",
        "C:\Program Files\Microsoft Visual Studio\2022\Community\VC\Tools\Llvm\x64\bin",
        "C:\Program Files\Microsoft Visual Studio\2022\Professional\VC\Tools\Llvm\x64\bin",
        "C:\Program Files\Microsoft Visual Studio\2022\Enterprise\VC\Tools\Llvm\x64\bin",
        "C:\Program Files\Microsoft Visual Studio\2022\BuildTools\VC\Tools\Llvm\x64\bin"
    )
    foreach ($candidate in $libclangCandidates) {
        if (Test-Path (Join-Path $candidate "libclang.dll")) {
            $env:LIBCLANG_PATH = $candidate
            break
        }
    }
}

if ($null -eq (Get-Command ninja -ErrorAction SilentlyContinue)) {
    $ninjaCandidates = @(
        "C:\Program Files\Microsoft Visual Studio\18\Community\Common7\IDE\CommonExtensions\Microsoft\CMake\Ninja",
        "C:\Program Files\Microsoft Visual Studio\2022\Community\Common7\IDE\CommonExtensions\Microsoft\CMake\Ninja",
        "C:\Program Files\Microsoft Visual Studio\2022\BuildTools\Common7\IDE\CommonExtensions\Microsoft\CMake\Ninja"
    )
    foreach ($candidate in $ninjaCandidates) {
        if (Test-Path (Join-Path $candidate "ninja.exe")) {
            $env:PATH = "$candidate;$env:PATH"
            break
        }
    }
}
