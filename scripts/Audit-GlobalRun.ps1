param([string]$RunDirectory)
$ErrorActionPreference='Stop'
. (Join-Path $PSScriptRoot 'ProjectPaths.ps1')
$projectPaths=Get-ProjectPaths
if (!$RunDirectory) {
    $latest=Join-Path $projectPaths.TrainingDir 'latest.txt'
    if(!(Test-Path -LiteralPath $latest)){ $latest=Join-Path $projectPaths.LegacyRunsDir 'latest.txt' }
    $id=(Get-Content -LiteralPath $latest -Raw).Trim()
    $RunDirectory=Join-Path (Split-Path -Parent $latest) $id
}
$manifest=Get-Content -LiteralPath (Join-Path $RunDirectory 'run.json') -Raw | ConvertFrom-Json
if ($manifest.status -ne 'SUCCESS') { throw "Run status is $($manifest.status), expected SUCCESS." }
$required=@('global-series-scaling.csv','global-validation-predictions.csv','global-item-seed-metrics.csv','global-epoch-history.csv','summary.txt','error-analysis.txt','report.html','performance-profile.json','console.log','calibration-selection.json','deployment/model.zip','deployment/model-metadata.json','deployment/item-scalers.csv','deployment/item-mapping.json','deployment/feature-schema.json','deployment/selection.json')
foreach($name in $required) { if(!(Test-Path -LiteralPath (Join-Path $RunDirectory $name) -PathType Leaf)){throw "Missing artifact: $name"} }
$metrics=(Import-Csv -LiteralPath (Join-Path $RunDirectory 'global-item-seed-metrics.csv')).Count
$predictions=(Import-Csv -LiteralPath (Join-Path $RunDirectory 'global-validation-predictions.csv')).Count
if($metrics -ne 150){throw "Expected 150 item-seed rows, found $metrics"}
if($predictions -ne 378000){throw "Expected 378000 prediction rows, found $predictions"}
$selection=Get-Content -LiteralPath (Join-Path $RunDirectory 'deployment/selection.json') -Raw | ConvertFrom-Json
Write-Host "AUDIT PASS: $RunDirectory"
Write-Host "Selected seed=$($selection.selectedSeed), epoch=$($selection.selectedEpoch), validationMacroRMSE=$($selection.validationMacroRMSE)"
