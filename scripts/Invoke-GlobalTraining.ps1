param([switch]$Preflight)

$ErrorActionPreference = 'Stop'
$projectRoot = Split-Path -Parent $PSScriptRoot
. (Join-Path $PSScriptRoot 'ProjectPaths.ps1')
$projectPaths = Get-ProjectPaths -Root $projectRoot
Initialize-ProjectPaths -Paths $projectPaths
Set-Location -LiteralPath $projectRoot
if (!(Test-Path -LiteralPath 'pom.xml' -PathType Leaf)) { throw 'Project root does not contain pom.xml.' }
if (!(Test-Path -LiteralPath $projectPaths.Dataset -PathType Leaf)) { throw "Missing training dataset: $($projectPaths.Dataset)" }
if (!(Get-Command java -ErrorAction SilentlyContinue)) { throw 'Java is not available in PATH.' }
if (!(Get-Command mvn -ErrorAction SilentlyContinue)) { throw 'Maven is not available in PATH.' }
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
    $calibrationRoot=Join-Path $projectPaths.BenchmarkDir 'global-calibration'
    $runId=(Get-Content -LiteralPath (Join-Path $calibrationRoot 'latest.txt') -Raw).Trim()
    $calibrationRuns += (Join-Path $calibrationRoot $runId)
}
$selectionPath=& (Join-Path $PSScriptRoot 'Select-GlobalCalibration.ps1') -RunDirectory $calibrationRuns
$selection=Get-Content -LiteralPath $selectionPath[-1] -Raw|ConvertFrom-Json
& (Join-Path $PSScriptRoot 'Invoke-GlobalRun.ps1') -Mode Full -TrainBatchSize $selection.selected.trainBatchSize -ValidationBatchSize $selection.selected.validationBatchSize
$fullRunId=(Get-Content -LiteralPath (Join-Path $projectPaths.TrainingDir 'latest.txt') -Raw).Trim()
Copy-Item -LiteralPath $selectionPath[-1] -Destination (Join-Path (Join-Path $projectPaths.TrainingDir $fullRunId) 'calibration-selection.json')
exit $LASTEXITCODE
