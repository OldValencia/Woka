param([string]$Output = 'build/startup-first-draw', [int]$Rounds = 3,
    [switch]$SkipTraining, [switch]$LauncherExe, [switch]$Capture, [switch]$ComparePrewarm,
    [switch]$CdsOnly)
$ErrorActionPreference = 'Stop'
$root = (Resolve-Path (Join-Path $PSScriptRoot '../../..')).Path
$app = Join-Path $root 'desktopApp/build/compose/binaries/main/app/Woka/app'
$java = [IO.Path]::GetFullPath((Join-Path $app '../runtime/bin/java.exe'))
$launcher = [IO.Path]::GetFullPath((Join-Path $app '../Woka.exe'))
$cfg = Join-Path $app 'Woka.cfg'
$originalCfg = [IO.File]::ReadAllBytes($cfg)
$cfgApplication = (Get-Content -LiteralPath $cfg) | Where-Object {
    -not $_.StartsWith('java-options=') -and $_ -ne '[JavaOptions]'
}
$out = [IO.Path]::GetFullPath((Join-Path $root $Output))
if (-not $out.StartsWith($root + [IO.Path]::DirectorySeparatorChar, [StringComparison]::OrdinalIgnoreCase)) {
    throw 'Probe output must stay inside the workspace.'
}
New-Item -ItemType Directory -Force -Path $out, "$out/storage", "$out/media" | Out-Null
[IO.File]::WriteAllText("$out/storage/.context_menu_initialized", '')
$jars = @(Get-Content -LiteralPath "$app/Woka.cfg" | Where-Object { $_.StartsWith('app.classpath=') } |
    ForEach-Object { $_.Substring('app.classpath='.Length).Replace('$APPDIR', $app) })
$classpath = $jars -join ';'
# An ordinary generated JPEG: separate first media draw from the empty shell.
Add-Type -AssemblyName System.Drawing
if ($Capture) { . (Join-Path $PSScriptRoot 'capture-window.ps1') }
$picture = [Drawing.Bitmap]::new(1920, 1080)
$graphics = [Drawing.Graphics]::FromImage($picture)
try {
    $graphics.Clear([Drawing.Color]::CornflowerBlue)
    $graphics.FillRectangle([Drawing.Brushes]::Gold, 400, 200, 1000, 600)
    $picture.Save("$out/media/sample.jpg", [Drawing.Imaging.ImageFormat]::Jpeg)
} finally { $graphics.Dispose(); $picture.Dispose() }
$common = @('-Xmx1536m', '-XX:MaxNewSize=256m', '-XX:+TieredCompilation',
    '-XX:Tier4CompileThreshold=100000', '-XX:CICompilerCount=2', '--enable-native-access=ALL-UNNAMED',
    '-Dcompose.application.configure.swing.globals=true', "-Dwoka.storageRoot=$out/storage",
    "-Dcompose.application.resources.dir=$app/resources", "-Dskiko.library.path=$app")
$main = @('-cp', $classpath, 'com.woka.MainKt', "$out/media/sample.jpg")
if ($ComparePrewarm) { $common += '-Dwoka.startup.mediaPrewarm=false' }
$results = [Collections.Generic.List[object]]::new()
function Read-ProbeText([string]$path) {
    try {
        $stream = [IO.File]::Open($path, [IO.FileMode]::Open, [IO.FileAccess]::Read, [IO.FileShare]::ReadWrite)
        $reader = [IO.StreamReader]::new($stream)
        try { return $reader.ReadToEnd() } finally { $reader.Dispose() }
    } catch [IO.IOException] { return '' }
}
function Invoke-Draw([string]$name, [string[]]$options, [int]$exitMs = 4000, [bool]$assembly = $false) {
    if ($LauncherExe -and -not $assembly) { $name = "exe-$name" }
    $timing = "$out/$name.csv"
    [IO.File]::WriteAllText($timing, '')
    [IO.File]::WriteAllText("$timing.process", '')
    $launchEpoch = [DateTimeOffset]::UtcNow.ToUnixTimeMilliseconds()
    $vmOptions = $common + $options + @("-Dwoka.probe.startup.output=$timing",
        "-Dwoka.probe.startup.exitMs=$exitMs", "-Dwoka.probe.startup.launchEpochMs=$launchEpoch")
    $arguments = $vmOptions + $main
    $executable = $java
    if ($LauncherExe -and -not $assembly) {
        # Only the generated workspace distribution; the installed app is never touched.
        $lines = $cfgApplication + '[JavaOptions]' + @($vmOptions | ForEach-Object { "java-options=$_" })
        [IO.File]::WriteAllLines($cfg, $lines, [Text.UTF8Encoding]::new($false))
        $executable = $launcher
        $arguments = @("$out/media/sample.jpg")
    }
    $quoted = ($arguments | ForEach-Object { '"' + $_ + '"' }) -join ' '
    $process = Start-Process -FilePath $executable -ArgumentList $quoted -PassThru -WindowStyle Hidden `
        -RedirectStandardOutput "$out/$name.out.log" -RedirectStandardError "$out/$name.err.log"
    $ownedHandle = $process.Handle
    $private = 0L
    $working = 0L
    $applicationProcess = $null
    $captured = $false
    $deadline = [DateTime]::UtcNow.AddSeconds(45)
    while (-not $process.HasExited -and [DateTime]::UtcNow -lt $deadline) {
        $process.Refresh()
        # jpackage's Woka.exe spawns another Woka.exe containing the JVM.
        # The instrumented JVM reports its own PID; never measure only the tiny launcher.
        if ($null -eq $applicationProcess -and (Get-Item "$timing.process").Length -gt 0) {
            $identity = (Read-ProbeText "$timing.process").Split(',')
            if ($identity.Length -eq 2) { $applicationProcess = Get-Process -Id ([int]$identity[0]) }
        }
        if ($null -ne $applicationProcess -and -not $applicationProcess.HasExited) {
            $applicationProcess.Refresh()
            $private = [Math]::Max($private, $applicationProcess.PrivateMemorySize64)
            $working = [Math]::Max($working, $applicationProcess.WorkingSet64)
            if ($Capture -and -not $captured -and (Read-ProbeText $timing).Contains('image-draw,')) {
                $captured = [StartupWindowCapture]::Save($applicationProcess.MainWindowHandle, "$out/$name.png")
            }
        }
        Start-Sleep -Milliseconds 50
    }
    if (-not $process.HasExited) {
        if ($null -ne $applicationProcess -and -not $applicationProcess.HasExited) {
            $applicationProcess.Kill()
        }
        Stop-Process -Id $process.Id
        throw "Probe timed out: $name (only its own process was stopped)"
    }
    $process.WaitForExit()
    if ($process.ExitCode -ne 0) { throw "Probe failed: $name exit=$($process.ExitCode); inspect $out/$name.err.log" }
    if ($assembly) { return }
    $stages = @{}
    Get-Content -LiteralPath $timing | ForEach-Object {
        $parts = $_.Split(',')
        $stages[$parts[0]] = [double]::Parse($parts[1], [Globalization.CultureInfo]::InvariantCulture)
    }
    if (-not $stages.ContainsKey('shell-draw')) { throw "No draw recorded: $name" }
    if (-not $stages.ContainsKey('image-draw')) { throw "No JPEG draw recorded: $name" }
    $row = [pscustomobject]@{Name=$name; MainMs=$stages['main']; IpcMs=$stages['ipc'];
        DiMs=$stages['di']; AwtMs=$stages['awt']; SkiaMs=$stages['skia']; WindowMs=$stages['window-bound'];
        ShellMs=$stages['shell-draw']; ImageMs=$stages['image-draw'];
        PeakPrivateMiB=[Math]::Round($private / 1MB); PeakWorkingMiB=[Math]::Round($working / 1MB)}
    $results.Add($row)
    $row | ConvertTo-Json -Compress | Write-Output
    $resultFile = if ($LauncherExe) { 'exe-results.csv' } else { 'results.csv' }
    $results | Export-Csv -LiteralPath "$out/$resultFile" -NoTypeInformation -Encoding UTF8
}
try {
if (-not $SkipTraining) {
Invoke-Draw 'jdk-cds' @('-Xshare:auto')
Invoke-Draw 'cds-train' @('-Xshare:auto', "-XX:ArchiveClassesAtExit=$out/ui.jsa") 5000
if (-not $CdsOnly) {
Invoke-Draw 'aot-record' @('-XX:AOTMode=record', "-XX:AOTConfiguration=$out/ui.aotconfig") 5000
}
}
if (-not $CdsOnly -and (-not $SkipTraining -or -not (Test-Path "$out/ui.aot"))) {
Invoke-Draw 'aot-create' @('-XX:AOTMode=create', "-XX:AOTConfiguration=$out/ui.aotconfig",
    "-XX:AOTCache=$out/ui.aot", '-Xlog:aot=info') 1200 $true
}
for ($round = 1; $round -le $Rounds; $round++) {
    Invoke-Draw "cds-$round" @('-Xshare:on', "-XX:SharedArchiveFile=$out/ui.jsa")
    if ($CdsOnly) { continue }
    if ($ComparePrewarm) {
        Invoke-Draw "cds-prewarm-$round" @('-Xshare:on', "-XX:SharedArchiveFile=$out/ui.jsa",
            '-Dwoka.startup.mediaPrewarm=true')
        Invoke-Draw "aot-$round" @('-XX:AOTMode=on', "-XX:AOTCache=$out/ui.aot", '-Xlog:aot=info')
        Invoke-Draw "aot-prewarm-$round" @('-XX:AOTMode=on', "-XX:AOTCache=$out/ui.aot", '-Xlog:aot=info',
            '-Dwoka.startup.mediaPrewarm=true')
        continue
    }
    Invoke-Draw "cds-small-$round" @('-Xms32m', '-Xshare:on', "-XX:SharedArchiveFile=$out/ui.jsa")
    Invoke-Draw "aot-$round" @('-XX:AOTMode=on', "-XX:AOTCache=$out/ui.aot", '-Xlog:aot=info')
    Invoke-Draw "aot-small-$round" @('-Xms32m', '-XX:AOTMode=on', "-XX:AOTCache=$out/ui.aot", '-Xlog:aot=info')
}
} finally {
    if ($LauncherExe) { [IO.File]::WriteAllBytes($cfg, $originalCfg) }
}
Write-Output 'Draw means completed Compose drawContent submission, not GPU scan-out; filesystem cache was not purged.'
