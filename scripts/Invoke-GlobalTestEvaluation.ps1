param(
    [Parameter(Mandatory = $true)]
    [string]$SourceRun
)

$ErrorActionPreference = 'Stop'
$projectRoot = Split-Path -Parent $PSScriptRoot
Set-Location -LiteralPath $projectRoot
$resolvedRun = (Resolve-Path -LiteralPath $SourceRun).Path
if (!(Test-Path -LiteralPath 'data/train.csv' -PathType Leaf)) { throw 'Missing data/train.csv in the project root.' }
if (Test-Path -LiteralPath (Join-Path $resolvedRun 'test-evaluation')) { throw 'Refusing to overwrite existing test-evaluation directory.' }

Write-Host 'Evaluation-only: no training, calibration, smoke, benchmark, or deployment update.'
mvn -B compile exec:java '-Dexec.mainClass=com.dl4j.salesforecast.GlobalTestEvaluationMain' "-Dexec.args=--source-run `"$resolvedRun`""
if ($LASTEXITCODE -ne 0) { throw "Global test evaluation failed with exit code $LASTEXITCODE." }
