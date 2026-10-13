[CmdletBinding()]
param(
    [Parameter(Position = 0)]
    [ValidateSet('help','setup','up','down','status','logs','ingest','forecast','smoke','test','train','evaluate','benchmark','results','clean')]
    [string]$Command = 'help',
    [string]$Service,
    [string]$File,
    [int]$Item,
    [string]$OriginDate,
    [string]$SourceRun,
    [switch]$ConfirmFullRun
)

$ErrorActionPreference = 'Stop'
$projectRoot = $PSScriptRoot
. (Join-Path $projectRoot 'scripts/ProjectPaths.ps1')
$projectPaths = Get-ProjectPaths -Root $projectRoot
Set-Location -LiteralPath $projectRoot

function Show-Help {
    @'
Sales Demand Forecasting project commands

  .\project.ps1 setup
  .\project.ps1 up | down | status
  .\project.ps1 logs [-Service frontend]
  .\project.ps1 ingest -File <csv>
  .\project.ps1 forecast -Item <1..50> -OriginDate <yyyy-MM-dd>
  .\project.ps1 smoke
  .\project.ps1 test
  .\project.ps1 train -ConfirmFullRun
  .\project.ps1 evaluate -SourceRun <run-directory>
  .\project.ps1 benchmark
  .\project.ps1 results
  .\project.ps1 clean

Full training is never started without -ConfirmFullRun. Clean removes only isolated build snapshots.
'@
}

function Assert-LastExitCode([string]$Activity) {
    if ($LASTEXITCODE -ne 0) { throw "$Activity failed with exit code $LASTEXITCODE." }
}

switch ($Command) {
    'help' { Show-Help }
    'setup' {
        foreach ($tool in 'java','mvn','docker') {
            if (!(Get-Command $tool -ErrorAction SilentlyContinue)) { throw "Missing prerequisite: $tool" }
        }
        if (!(Test-Path -LiteralPath '.env')) { Copy-Item -LiteralPath '.env.example' -Destination '.env' }
        Initialize-ProjectPaths -Paths $projectPaths
        if (!(Test-Path -LiteralPath $projectPaths.Dataset -PathType Leaf)) { throw "Missing dataset: $($projectPaths.Dataset)" }
        $modelDir = if ($env:MODEL_ARTIFACT_DIR) { $env:MODEL_ARTIFACT_DIR } else { 'output/runs/2026-10-13_025422_624_gpu/deployment' }
        if (!(Test-Path -LiteralPath (Join-Path $modelDir 'model.zip') -PathType Leaf)) { throw "Missing deployment model: $modelDir/model.zip" }
        Write-Host "SETUP READY`nDataset: $($projectPaths.Dataset)`nModel: $modelDir"
    }
    'up' {
        & mvn -B -Pcpu-service package; Assert-LastExitCode 'Maven package'
        & docker compose config --quiet; Assert-LastExitCode 'Docker Compose validation'
        & docker compose up --build -d; Assert-LastExitCode 'Docker Compose startup'
    }
    'down' { & docker compose down; Assert-LastExitCode 'Docker Compose shutdown' }
    'status' {
        & docker compose ps; Assert-LastExitCode 'Docker Compose status'
        foreach ($url in 'http://localhost:3000/','http://localhost:3000/api/health','http://localhost:8083/health') {
            try { $response = Invoke-WebRequest -UseBasicParsing -TimeoutSec 10 -Uri $url; Write-Host "$($response.StatusCode) $url" }
            catch { Write-Warning "Unavailable: $url ($($_.Exception.Message))" }
        }
    }
    'logs' {
        if ($Service) { & docker compose logs --tail 200 $Service } else { & docker compose logs --tail 200 }
        Assert-LastExitCode 'Docker Compose logs'
    }
    'ingest' {
        if (!$File) { throw 'ingest requires -File <csv>.' }
        $resolved = (Resolve-Path -LiteralPath $File).Path
        & curl.exe -f -sS -F "file=@$resolved" http://localhost:3000/api/pipelines/ingest
        Assert-LastExitCode 'CSV ingestion'
    }
    'forecast' {
        if ($Item -lt 1 -or $Item -gt 50 -or $OriginDate -notmatch '^\d{4}-\d{2}-\d{2}$') {
            throw 'forecast requires -Item 1..50 and -OriginDate yyyy-MM-dd.'
        }
        $body = @{ itemId = $Item; originDate = $OriginDate } | ConvertTo-Json
        Invoke-RestMethod -Method Post -Uri 'http://localhost:3000/api/forecasts' -ContentType 'application/json' -Body $body | ConvertTo-Json -Depth 8
    }
    'smoke' { & scripts/Invoke-GlobalRun.ps1 -Mode Smoke; Assert-LastExitCode 'Global smoke run' }
    'test' { & mvn -B test; Assert-LastExitCode 'Maven tests' }
    'train' {
        if (!$ConfirmFullRun) { throw 'Full training requires -ConfirmFullRun.' }
        & scripts/Invoke-GlobalTraining.ps1; Assert-LastExitCode 'Global training workflow'
    }
    'evaluate' {
        if (!$SourceRun) { throw 'evaluate requires -SourceRun <run-directory>.' }
        & scripts/Invoke-GlobalTestEvaluation.ps1 -SourceRun $SourceRun; Assert-LastExitCode 'Checkpoint-only evaluation'
    }
    'benchmark' { & scripts/Invoke-GlobalRun.ps1 -Mode Benchmark; Assert-LastExitCode 'Global runtime benchmark' }
    'results' {
        Initialize-ProjectPaths -Paths $projectPaths
        foreach ($entry in @(
            @{ Name='training'; Path=$projectPaths.TrainingDir },
            @{ Name='evaluation'; Path=$projectPaths.EvaluationDir },
            @{ Name='smoke-tests'; Path=$projectPaths.SmokeDir },
            @{ Name='benchmarks'; Path=$projectPaths.BenchmarkDir },
            @{ Name='legacy-runs'; Path=$projectPaths.LegacyRunsDir }
        )) {
            $latest = Join-Path $entry.Path 'latest.txt'
            $value = if (Test-Path -LiteralPath $latest) { (Get-Content -LiteralPath $latest -Raw).Trim() } else { '(none)' }
            Write-Host "$($entry.Name): $($entry.Path) | latest: $value"
        }
    }
    'clean' {
        $snapshotRoot = [IO.Path]::GetFullPath((Join-Path $projectPaths.LogDir 'build-snapshots'))
        $expectedParent = [IO.Path]::GetFullPath($projectPaths.LogDir)
        if (!$snapshotRoot.StartsWith($expectedParent + [IO.Path]::DirectorySeparatorChar, [StringComparison]::OrdinalIgnoreCase)) {
            throw "Unsafe clean target: $snapshotRoot"
        }
        if (Test-Path -LiteralPath $snapshotRoot) { Remove-Item -LiteralPath $snapshotRoot -Recurse -Force }
        Write-Host "Removed isolated build snapshots only: $snapshotRoot"
    }
}
