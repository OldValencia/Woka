param(
    [Parameter(Mandatory=$true)][string]$Ffmpeg,
    [string]$Samples = 'D:/Movies/DV samples',
    [string]$Output,
    [switch]$Premap
)
$ErrorActionPreference = 'Stop'
if (-not $Output) { $Output = Join-Path $PSScriptRoot '../../build/dv-p5-reference/frames' }
$Ffmpeg = (Resolve-Path -LiteralPath $Ffmpeg).Path
New-Item -ItemType Directory -Force -Path $Output | Out-Null
$Output = (Resolve-Path -LiteralPath $Output).Path
$files = @('Mystery Box Dolby Vision Profile 5.mp4', 'Test Jellyfin 1080p DV P5.mp4')
$manifest = @()
& $Ffmpeg -version | Set-Content -Encoding UTF8 (Join-Path $Output 'version.txt')
Get-FileHash -LiteralPath $Ffmpeg -Algorithm SHA256 | Format-List | Out-String |
    Set-Content -Encoding UTF8 (Join-Path $Output 'binary-sha256.txt')
for ($index = 0; $index -lt $files.Count; $index++) {
    $inputFile = Join-Path $Samples $files[$index]
    $sourceHash = (Get-FileHash -LiteralPath $inputFile -Algorithm SHA256).Hash
    foreach ($second in @(1, 5, 10)) {
        # Both samples: frame 60/300/600. Mystery Box is 60000/1001 fps,
        # hence PTS 1.001/5.005/10.010; Jellyfin is exactly 60 fps.
        $frame = $second * 60
        $end = $frame + 1
        foreach ($apply in @('true', 'false')) {
            if ($apply -eq 'false' -and $second -ne 5) { continue }
            $stem = "p5-$index-$second-dovi-$apply"
            $filter = "trim=end_frame=$end,select=eq(n\,$frame),showinfo,libplacebo=" +
                "format=gbrpf32le:colorspace=gbr:color_primaries=bt2020:color_trc=smpte2084:" +
                "range=full:apply_dolbyvision=$apply`:apply_filmgrain=false:peak_detect=false:" +
                'tonemapping=clip:gamut_mode=clip:dithering=none:contrast_recovery=0:' +
                'upscaler=bilinear:downscaler=bilinear'
            if ($Premap) {
                # P010 left-aligns the same ten bits in R16 UNORM. Low-aligned
                # yuv420p10le amplifies hardware texture-filter precision loss.
                $filter = $filter.Replace('libplacebo=', 'format=p010le,libplacebo=') +
                    ':custom_shader_path=natives/dovi/reference-premap.glsl'
            }
            $target = Join-Path $Output "$stem.gbrpf32le"
            $log = Join-Path $Output "$stem.log"
            $arguments = @('-hide_banner', '-loglevel', 'verbose', '-threads', '4', '-i', $inputFile,
                '-an', '-vf', $filter, '-frames:v', '1', '-fps_mode', 'passthrough',
                '-f', 'rawvideo', '-y', $target)
            # Native stderr is diagnostic output, not a PowerShell terminating error.
            $previous = $ErrorActionPreference
            $ErrorActionPreference = 'Continue'
            & $Ffmpeg @arguments *> $log
            $result = $LASTEXITCODE
            $ErrorActionPreference = $previous
            if ($result -ne 0) { throw "Reference failed: $stem; see $log" }
            $renderFailure = Get-Content -Raw -LiteralPath $log
            if ($renderFailure -match 'Failed rendering|Failed creating FBO|Disabling advanced rendering|shaderc compile status.*error') {
                throw "Reference renderer reported failure despite process exit code: $log"
            }
            $expected = if ($index -eq 0) { 3840L * 2160 * 12 } else { 1920L * 1080 * 12 }
            if ((Get-Item -LiteralPath $target).Length -ne $expected) { throw "Wrong output size: $stem" }
            $rawHash = $null
            if ($apply -eq 'true') {
                $rawTarget = Join-Path $Output "p5-$index-$second.yuv420p10le"
                $rawArgs = @('-hide_banner', '-loglevel', 'error', '-threads', '4', '-i', $inputFile,
                    '-an', '-vf', "trim=end_frame=$end,select=eq(n\,$frame)", '-frames:v', '1',
                    '-fps_mode', 'passthrough', '-pix_fmt', 'yuv420p10le', '-f', 'rawvideo', '-y', $rawTarget)
                $ErrorActionPreference = 'Continue'
                & $Ffmpeg @rawArgs *> (Join-Path $Output "$stem-input.log")
                $result = $LASTEXITCODE
                $ErrorActionPreference = $previous
                if ($result -ne 0) { throw "Reference input decode failed: $stem" }
                $rawHash = (Get-FileHash $rawTarget -Algorithm SHA256).Hash
            }
            $manifest += [pscustomobject]@{
                name=$stem; source=$inputFile; source_sha256=$sourceHash; frame=$frame;
                pts_us=if ($index -eq 0) { $second * 1001000L } else { $second * 1000000L };
                filter=$filter; output_sha256=(Get-FileHash $target -Algorithm SHA256).Hash;
                input_pixels_sha256=$rawHash
            }
            Write-Host "REFERENCE $stem complete"
        }
    }
}
$manifest | ConvertTo-Json -Depth 4 | Set-Content -Encoding UTF8 (Join-Path $Output 'manifest.json')
