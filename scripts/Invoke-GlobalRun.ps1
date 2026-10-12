param(
    [ValidateSet('Smoke', 'Benchmark', 'Preflight', 'Full')]
    [string]$Mode = 'Smoke'
)

$ErrorActionPreference = 'Stop'
$projectRoot = Split-Path -Parent $PSScriptRoot
Set-Location -LiteralPath $projectRoot
if (!(Test-Path -LiteralPath 'data/train.csv' -PathType Leaf)) {
    throw 'Missing data/train.csv in the project root.'
}

# Keep runtime classes outside the IDE output directory. Each invocation gets a new snapshot.
$runStamp = (Get-Date -Format 'yyyy-MM-dd_HHmmss_fff') + '-' + [guid]::NewGuid().ToString('N').Substring(0, 8)
$buildSnapshot = Join-Path $projectRoot ('output/global-build-' + $runStamp)
New-Item -ItemType Directory -Path $buildSnapshot | Out-Null
Copy-Item -LiteralPath 'pom.xml' -Destination (Join-Path $buildSnapshot 'pom.xml')
Copy-Item -LiteralPath 'src' -Destination (Join-Path $buildSnapshot 'src') -Recurse
$globalRunLog = Join-Path $projectRoot ('output/global-' + $Mode.ToLowerInvariant() + '-' + $runStamp + '.log')
$globalArgs = switch ($Mode) {
    'Smoke' { '--global-store-smoke=1' }
    'Benchmark' { '--global-store-benchmark=1' }
    'Preflight' { '--global-preflight --global-store=1 --seeds=42,123,2026' }
    'Full' { '--global-store=1 --seeds=42,123,2026' }
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
