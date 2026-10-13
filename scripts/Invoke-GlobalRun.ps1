param(
    [ValidateSet('Smoke', 'Benchmark', 'Preflight', 'Calibration', 'Full')]
    [string]$Mode = 'Smoke',
    [ValidateSet(64,128,256)]
    [int]$TrainBatchSize = 64,
    [ValidateSet(64,256,512,1024)]
    [int]$ValidationBatchSize = 64
)

$ErrorActionPreference = 'Stop'
$projectRoot = Split-Path -Parent $PSScriptRoot
. (Join-Path $PSScriptRoot 'ProjectPaths.ps1')
$projectPaths = Get-ProjectPaths -Root $projectRoot
Initialize-ProjectPaths -Paths $projectPaths
Set-Location -LiteralPath $projectRoot
if (!(Test-Path -LiteralPath $projectPaths.Dataset -PathType Leaf)) {
    throw "Missing training dataset: $($projectPaths.Dataset)"
}

# Keep runtime classes outside the IDE output directory. Each invocation gets a new snapshot.
$runStamp = (Get-Date -Format 'yyyy-MM-dd_HHmmss_fff') + '-' + [guid]::NewGuid().ToString('N').Substring(0, 8)
$snapshotRoot = Join-Path $projectPaths.LogDir 'build-snapshots'
New-Item -ItemType Directory -Force -Path $snapshotRoot | Out-Null
$buildSnapshot = Join-Path $snapshotRoot ('global-' + $runStamp)
New-Item -ItemType Directory -Path $buildSnapshot | Out-Null
Copy-Item -LiteralPath 'pom.xml' -Destination (Join-Path $buildSnapshot 'pom.xml')
Copy-Item -LiteralPath 'src' -Destination (Join-Path $buildSnapshot 'src') -Recurse
$modeLogRoot = Join-Path $projectPaths.LogDir ('global-' + $Mode.ToLowerInvariant())
New-Item -ItemType Directory -Force -Path $modeLogRoot | Out-Null
$globalRunLog = Join-Path $modeLogRoot ($runStamp + '.log')
$globalArgs = switch ($Mode) {
    'Smoke' { "--global-store-smoke=1 --batch-size=$TrainBatchSize --validation-batch-size=$ValidationBatchSize" }
    'Benchmark' { '--global-store-benchmark=1' }
    'Preflight' { "--global-preflight --global-store=1 --seeds=42,123,2026 --batch-size=$TrainBatchSize --validation-batch-size=$ValidationBatchSize" }
    'Calibration' { "--global-store-calibration=1 --batch-size=$TrainBatchSize --validation-batch-size=$ValidationBatchSize" }
    'Full' { "--global-store=1 --seeds=42,123,2026 --batch-size=$TrainBatchSize --validation-batch-size=$ValidationBatchSize" }
}

Write-Host "Build snapshot: $buildSnapshot"
Write-Host "Complete log: $globalRunLog"
try {
    # Windows PowerShell 5 treats native stderr as ErrorRecords; Java INFO on stderr
    # must not terminate the run. The native exit code remains the success criterion.
    $ErrorActionPreference = 'Continue'
    mvn -B -f (Join-Path $buildSnapshot 'pom.xml') compile exec:java "-Dexec.args=$globalArgs" 2>&1 |
        Tee-Object -FilePath $globalRunLog
    $globalExitCode = $LASTEXITCODE
} finally {
    $ErrorActionPreference = 'Stop'
}
Set-Content -LiteralPath ($globalRunLog + '.exit') -Value $globalExitCode
Write-Host "Exit code: $globalExitCode"
if ($globalExitCode -ne 0) { throw "Global run failed with exit code $globalExitCode. See $globalRunLog" }
