param(
    [string]$BuildDir = (Join-Path $PSScriptRoot '../../build/dovi-native'),
    [string]$SourceArchive,
    [string]$OutDir = (Join-Path $PSScriptRoot '../../desktopApp/appResources/windows-x64')
)
$ErrorActionPreference = 'Stop'
$version = '8.1.2'
$expectedHash = '9fd092511605bbebafe095ea6d38d9e40f34d12f7386e1258372df8be0576eb7'
New-Item -ItemType Directory -Force -Path $BuildDir | Out-Null
$BuildDir = (Resolve-Path -LiteralPath $BuildDir).Path
if (-not $SourceArchive) {
    $SourceArchive = Join-Path $BuildDir "ffmpeg-n$version.tar.gz"
    if (-not (Test-Path -LiteralPath $SourceArchive)) {
        Invoke-WebRequest -Uri "https://codeload.github.com/FFmpeg/FFmpeg/tar.gz/refs/tags/n$version" `
            -OutFile $SourceArchive -UseBasicParsing
    }
}
$SourceArchive = (Resolve-Path -LiteralPath $SourceArchive).Path
if ((Get-FileHash -LiteralPath $SourceArchive -Algorithm SHA256).Hash.ToLowerInvariant() -ne $expectedHash) {
    throw 'FFmpeg source hash mismatch; nothing is built'
}
$source = Join-Path $BuildDir "FFmpeg-n$version"
$vswhere = Join-Path ${env:ProgramFiles(x86)} 'Microsoft Visual Studio/Installer/vswhere.exe'
$vs = & $vswhere -latest -prerelease -products * `
    -requires Microsoft.VisualStudio.Component.VC.Tools.x86.x64 -property installationPath
if (-not $vs) { throw 'Visual Studio x64 C++ tools are required' }
$vcvars = Join-Path $vs 'VC/Auxiliary/Build/vcvars64.bat'
$cmakeRoot = Join-Path $vs 'Common7/IDE/CommonExtensions/Microsoft/CMake'
$cmake = Join-Path $cmakeRoot 'CMake/bin/cmake.exe'
$ninja = Join-Path $cmakeRoot 'Ninja'
if (-not (Test-Path -LiteralPath (Join-Path $source 'libavutil/dovi_meta.h'))) {
    Push-Location -LiteralPath $BuildDir
    try {
        & $cmake -E tar xzf $SourceArchive
        if ($LASTEXITCODE -ne 0) { throw 'FFmpeg source extraction failed' }
    } finally {
        Pop-Location
    }
}
$compile = Join-Path $BuildDir 'compile'
$commands = @"
@echo off
set "PATH=%SystemRoot%\System32;%SystemRoot%;%PATH%"
call "$vcvars" >nul || exit /b 1
set "PATH=$ninja;%PATH%"
"$cmake" -S "$PSScriptRoot" -B "$compile" -G Ninja -DCMAKE_BUILD_TYPE=Release -DFFMPEG_SOURCE_DIR="$source" || exit /b 1
"$cmake" --build "$compile" --config Release || exit /b 1
"@
$commandFile = Join-Path $BuildDir 'build_woka_dovi.cmd'
Set-Content -LiteralPath $commandFile -Value $commands -Encoding ASCII
& $env:ComSpec /c "`"$commandFile`""
if ($LASTEXITCODE -ne 0) { throw "DV bridge build failed ($LASTEXITCODE)" }
New-Item -ItemType Directory -Force -Path $OutDir | Out-Null
Copy-Item -LiteralPath (Join-Path $compile 'woka_dovi.dll') -Destination $OutDir -Force
Write-Host "Built woka_dovi.dll -> $OutDir"
