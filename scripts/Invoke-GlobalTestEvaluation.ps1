param(
    [Parameter(Mandatory = $true)]
    [string]$SourceRun
)

$ErrorActionPreference = 'Stop'
$projectRoot = Split-Path -Parent $PSScriptRoot
. (Join-Path $PSScriptRoot 'ProjectPaths.ps1')
$projectPaths = Get-ProjectPaths -Root $projectRoot
Initialize-ProjectPaths -Paths $projectPaths
Set-Location -LiteralPath $projectRoot
$resolvedRun = (Resolve-Path -LiteralPath $SourceRun).Path
if (!(Test-Path -LiteralPath $projectPaths.Dataset -PathType Leaf)) { throw "Missing training dataset: $($projectPaths.Dataset)" }
$evaluationDirectory = Join-Path $projectPaths.EvaluationDir ((Split-Path -Leaf $resolvedRun) + '_test-2017')
if (Test-Path -LiteralPath $evaluationDirectory) { throw "Refusing to overwrite existing evaluation: $evaluationDirectory" }

Write-Host 'Evaluation-only: no training, calibration, smoke, benchmark, or deployment update.'
$evaluationLogRoot = Join-Path $projectPaths.LogDir 'evaluation'
New-Item -ItemType Directory -Force -Path $evaluationLogRoot | Out-Null
$evaluationLog = Join-Path $evaluationLogRoot ((Split-Path -Leaf $resolvedRun) + '-' + (Get-Date -Format 'yyyy-MM-dd_HHmmss') + '.log')
$mavenArguments = @(
    '-B'
    '-Dexec.mainClass=com.dl4j.salesforecast.GlobalTestEvaluationMain'
    "-Dexec.args=--source-run `"$resolvedRun`""
    'compile'
    'exec:java'
)
$ErrorActionPreference = 'Continue'
& mvn @mavenArguments 2>&1 | Tee-Object -FilePath $evaluationLog
$evaluationExitCode = $LASTEXITCODE
$ErrorActionPreference = 'Stop'
if ($evaluationExitCode -ne 0) { throw "Global test evaluation failed with exit code $evaluationExitCode. See $evaluationLog" }
