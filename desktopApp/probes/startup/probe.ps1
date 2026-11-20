param([string]$Output = 'build/startup-analysis')
$ErrorActionPreference = 'Stop'
$root = (Resolve-Path (Join-Path $PSScriptRoot '../../..')).Path
$app = Join-Path $root 'desktopApp/build/compose/binaries/main/app/Woka/app'
$java = Join-Path $app '../runtime/bin/java.exe'
$out = [IO.Path]::GetFullPath((Join-Path $root $Output))
if (-not $out.StartsWith($root + [IO.Path]::DirectorySeparatorChar, [StringComparison]::OrdinalIgnoreCase)) {
    throw 'Probe output must stay inside the workspace.'
}
New-Item -ItemType Directory -Force -Path $out, "$out/classes", "$out/storage" | Out-Null
$jars = @(Get-Content -LiteralPath "$app/Woka.cfg" | Where-Object { $_.StartsWith('app.classpath=') } |
    ForEach-Object { $_.Substring('app.classpath='.Length).Replace('$APPDIR', $app) })
if ($jars.Count -eq 0) { throw 'Build :desktopApp:createDistributable first.' }
$classpath = $jars -join ';'
& javac -cp $classpath -d "$out/classes" "$PSScriptRoot/AotBootstrap.java"
if ($LASTEXITCODE -ne 0) { throw 'Probe compilation failed.' }
& jar --create --file "$out/probe.jar" -C "$out/classes" .
if ($LASTEXITCODE -ne 0) { throw 'Probe packaging failed.' }
$common = @('-Xmx1536m', '-XX:MaxNewSize=256m', '-XX:+TieredCompilation',
    '-XX:Tier4CompileThreshold=100000', '-XX:CICompilerCount=2', '--enable-native-access=ALL-UNNAMED',
    '-Djava.awt.headless=true', "-Dwoka.storageRoot=$out/storage")
$main = @('-cp', "$out/probe.jar;$classpath", 'AotBootstrap')
$measurements = [Collections.Generic.List[object]]::new()
function Run-Probe([string]$name, [string[]]$options) {
    $log = Join-Path $out "$name.log"
    $watch = [Diagnostics.Stopwatch]::StartNew()
    & $java @common @options @main > $log 2>&1
    $watch.Stop()
    if ($LASTEXITCODE -ne 0) { throw "Probe failed: $log" }
    $lines = Get-Content -LiteralPath $log
    $lines | Where-Object { $_ -match 'BOOTSTRAP_OK|AOT-linked|aot-linked' }
    foreach ($line in $lines) {
        if ($line -match 'BOOTSTRAP_OK elapsedMs=([\d.]+) heapUsedMiB=(\d+) heapCommittedMiB=(\d+)') {
            $measurements.Add([pscustomobject]@{ Name=$name; ProcessMs=$watch.Elapsed.TotalMilliseconds;
                BootstrapMs=$Matches[1]; HeapUsedMiB=$Matches[2]; HeapCommittedMiB=$Matches[3] })
        }
    }
}
Run-Probe 'baseline' @('-Xshare:auto')
Run-Probe 'small-heap' @('-Xms32m', '-Xshare:auto')
Run-Probe 'cds-record' @('-Xshare:auto', "-XX:ArchiveClassesAtExit=$out/bootstrap.jsa")
Run-Probe 'record' @('-XX:AOTMode=record', "-XX:AOTConfiguration=$out/bootstrap.aotconfig")
Run-Probe 'create' @('-XX:AOTMode=create', "-XX:AOTConfiguration=$out/bootstrap.aotconfig",
    "-XX:AOTCache=$out/bootstrap.aot", '-Xlog:aot=info')
Run-Probe 'aot' @('-XX:AOTMode=on', "-XX:AOTCache=$out/bootstrap.aot", '-Xlog:aot=info')
Run-Probe 'aot-small-heap' @('-Xms32m', '-XX:AOTMode=on', "-XX:AOTCache=$out/bootstrap.aot", '-Xlog:aot=info')
for ($round = 1; $round -le 5; $round++) {
    Run-Probe "cds-$round" @('-Xshare:on', "-XX:SharedArchiveFile=$out/bootstrap.jsa")
    Run-Probe "aot-$round" @('-XX:AOTMode=on', "-XX:AOTCache=$out/bootstrap.aot")
    Run-Probe "aot-small-$round" @('-Xms32m', '-XX:AOTMode=on', "-XX:AOTCache=$out/bootstrap.aot")
}
$measurements | Export-Csv -LiteralPath "$out/measurements.csv" -NoTypeInformation -Encoding UTF8
Write-Output "Compatibility logs: $out. These timings exclude window/first draw and are not cold-start results."
