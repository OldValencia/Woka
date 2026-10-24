# Builds woka_jxr.dll (Release, x64, static CRT) and copies it to desktopApp/appResources/windows-x64/.
# The pinned jxrlib archive is downloaded and SHA-256-checked by CMake (see CMakeLists.txt).
# Usage: ./build.ps1 [-BuildDir <dir>]
param(
    [string]$BuildDir = (Join-Path ([System.IO.Path]::GetTempPath()) 'woka_jxr_build')
)
$ErrorActionPreference = 'Stop'

$here = $PSScriptRoot
$dest = Join-Path $here '..\..\desktopApp\appResources\windows-x64'

# Locate Visual Studio (any edition with the x64 C++ toolset).
$vswhere = Join-Path ${env:ProgramFiles(x86)} 'Microsoft Visual Studio\Installer\vswhere.exe'
$vs = $null
if (Test-Path $vswhere) {
    $vs = & $vswhere -latest -prerelease -products * `
        -requires Microsoft.VisualStudio.Component.VC.Tools.x86.x64 -property installationPath
}
if (-not $vs) { $vs = 'C:\Program Files\Microsoft Visual Studio\18\Community' }
$vcvars = Join-Path $vs 'VC\Auxiliary\Build\vcvars64.bat'
if (-not (Test-Path $vcvars)) { throw "vcvars64.bat not found under $vs" }

# Prefer the CMake/Ninja bundled with Visual Studio; fall back to PATH.
$cmakeDir = Join-Path $vs 'Common7\IDE\CommonExtensions\Microsoft\CMake'
$cmake = Join-Path $cmakeDir 'CMake\bin\cmake.exe'
if (-not (Test-Path $cmake)) { $cmake = 'cmake' }
$ninjaDir = Join-Path $cmakeDir 'Ninja'
$generator = if (Test-Path (Join-Path $ninjaDir 'ninja.exe')) { 'Ninja' } else { 'NMake Makefiles' }

New-Item -ItemType Directory -Force $BuildDir | Out-Null
$script = @"
@echo off
rem vcvars needs findstr/vswhere; some shells start with a PATH that lacks them.
set "PATH=%SystemRoot%\System32;%SystemRoot%;%ProgramFiles(x86)%\Microsoft Visual Studio\Installer;%PATH%"
call "$vcvars" >nul || exit /b 1
set "PATH=$ninjaDir;%PATH%"
"$cmake" -S "$here" -B "$BuildDir" -G "$generator" -DCMAKE_BUILD_TYPE=Release || exit /b 1
"$cmake" --build "$BuildDir" --config Release || exit /b 1
"@
$bat = Join-Path $BuildDir 'build_woka_jxr.cmd'
Set-Content -Path $bat -Value $script -Encoding ASCII
$cmd = if ($env:ComSpec) { $env:ComSpec } else { Join-Path $env:SystemRoot 'System32\cmd.exe' }
& $cmd /c "`"$bat`""
if ($LASTEXITCODE -ne 0) { throw "build failed ($LASTEXITCODE)" }

New-Item -ItemType Directory -Force $dest | Out-Null
Copy-Item (Join-Path $BuildDir 'woka_jxr.dll') $dest -Force
Write-Host "copied woka_jxr.dll -> $((Resolve-Path $dest).Path)"
