# Reproduces desktopApp/appResources/windows-x64/ for JPEG XL decoding.
#
# Downloads the official libjxl release zip (pinned version + SHA-256), verifies it,
# and copies the minimal DLL set jxl.dll needs at runtime. Nothing is compiled.
#
# Usage (from any directory):
#   pwsh -File natives/jxl/build.ps1
#   pwsh -File natives/jxl/build.ps1 -OutDir C:\somewhere\else -WorkDir C:\tmp\jxl
#
# MSVC runtime: jxl.dll / jxl_cms.dll / jxl_threads.dll import MSVCP140.dll and VCRUNTIME140.dll,
# which are NOT in the zip. The JDK runtime that Compose bundles ships both in runtime/bin
# (and awt.dll already pulls MSVCP140 into the process), so they are deliberately not copied here.

[CmdletBinding()]
param(
    [string] $OutDir = (Join-Path $PSScriptRoot '..\..\desktopApp\appResources\windows-x64'),
    [string] $WorkDir = (Join-Path ([System.IO.Path]::GetTempPath()) 'woka-jxl-windows')
)

$ErrorActionPreference = 'Stop'

$Version = '0.12.0'
$Url = "https://github.com/libjxl/libjxl/releases/download/v$Version/jxl-x64-windows.zip"
$Sha256 = '4ee3f72134a2a49774c357f2bffecf30d752d702147896c493578789de15a97c'

# Load order, leaves first. jxl_threads is independent of the rest.
$Dlls = @(
    'brotlicommon.dll',
    'brotlidec.dll',
    'brotlienc.dll',
    'jxl_cms.dll',
    'jxl.dll',
    'jxl_threads.dll'
)

New-Item -ItemType Directory -Force -Path $WorkDir | Out-Null
$Zip = Join-Path $WorkDir "jxl-x64-windows-v$Version.zip"

if (-not (Test-Path $Zip)) {
    Write-Host "Downloading $Url"
    Invoke-WebRequest -Uri $Url -OutFile $Zip -UseBasicParsing
}

$Actual = (Get-FileHash -Algorithm SHA256 -Path $Zip).Hash.ToLowerInvariant()
if ($Actual -ne $Sha256) {
    Remove-Item -LiteralPath $Zip -Force
    throw "SHA-256 mismatch for $Zip`n  expected $Sha256`n  actual   $Actual"
}
Write-Host "SHA-256 OK ($Sha256)"

$Extract = Join-Path $WorkDir "extract-v$Version"
if (Test-Path $Extract) {
    Remove-Item -LiteralPath $Extract -Recurse -Force
}
Expand-Archive -LiteralPath $Zip -DestinationPath $Extract
$Bin = Join-Path $Extract 'x64-windows\bin'

New-Item -ItemType Directory -Force -Path $OutDir | Out-Null
foreach ($Dll in $Dlls) {
    $Source = Join-Path $Bin $Dll
    if (-not (Test-Path $Source)) {
        throw "Missing $Dll in the release zip"
    }
    Copy-Item -LiteralPath $Source -Destination $OutDir -Force
    Write-Host "copied $Dll"
}

Write-Host "Done -> $((Resolve-Path $OutDir).Path)"
Write-Host "Load order: $($Dlls -join ' -> ')"
