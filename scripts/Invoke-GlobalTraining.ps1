param([switch]$Preflight)

$ErrorActionPreference = 'Stop'
$projectRoot = Split-Path -Parent $PSScriptRoot
Set-Location -LiteralPath $projectRoot
if (!(Test-Path -LiteralPath 'pom.xml' -PathType Leaf)) { throw 'Project root does not contain pom.xml.' }
if (!(Test-Path -LiteralPath 'data/train.csv' -PathType Leaf)) { throw 'Missing data/train.csv.' }
if (!(Get-Command java -ErrorAction SilentlyContinue)) { throw 'Java is not available in PATH.' }
if (!(Get-Command mvn -ErrorAction SilentlyContinue)) { throw 'Maven is not available in PATH.' }
New-Item -ItemType Directory -Force -Path 'output','output/runs' | Out-Null
java -version
if ($LASTEXITCODE -ne 0) { exit $LASTEXITCODE }
mvn -version
if ($LASTEXITCODE -ne 0) { exit $LASTEXITCODE }
if ($Preflight) {
    & (Join-Path $PSScriptRoot 'Invoke-GlobalRun.ps1') -Mode Preflight -TrainBatchSize 64 -ValidationBatchSize 256
    exit $LASTEXITCODE
}
& (Join-Path $PSScriptRoot 'Invoke-GlobalRun.ps1') -Mode Preflight -TrainBatchSize 64 -ValidationBatchSize 256
& (Join-Path $PSScriptRoot 'Invoke-GlobalRun.ps1') -Mode Smoke -TrainBatchSize 64 -ValidationBatchSize 256
$calibrationRuns=@()
foreach($pair in @(@(64,256),@(128,512),@(256,1024))){
    & (Join-Path $PSScriptRoot 'Invoke-GlobalRun.ps1') -Mode Calibration -TrainBatchSize $pair[0] -ValidationBatchSize $pair[1]
    $runId=(Get-Content -LiteralPath 'output/runs/latest.txt' -Raw).Trim()
    $calibrationRuns += (Join-Path 'output/runs' $runId)
}
$selectionPath=& (Join-Path $PSScriptRoot 'Select-GlobalCalibration.ps1') -RunDirectory $calibrationRuns
$selection=Get-Content -LiteralPath $selectionPath[-1] -Raw|ConvertFrom-Json
& (Join-Path $PSScriptRoot 'Invoke-GlobalRun.ps1') -Mode Full -TrainBatchSize $selection.selected.trainBatchSize -ValidationBatchSize $selection.selected.validationBatchSize
$fullRunId=(Get-Content -LiteralPath 'output/runs/latest.txt' -Raw).Trim()
Copy-Item -LiteralPath $selectionPath[-1] -Destination (Join-Path (Join-Path 'output/runs' $fullRunId) 'calibration-selection.json')
exit $LASTEXITCODE
