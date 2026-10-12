param(
    [Parameter(Mandatory=$true)][string[]]$RunDirectory,
    [double]$MaxRmseRegressionPercent = 1.0,
    [string]$OutputPath
)
$ErrorActionPreference='Stop'
if($MaxRmseRegressionPercent -lt 0){throw 'MaxRmseRegressionPercent must be non-negative.'}
$candidates=@()
foreach($directory in $RunDirectory){
    $manifest=Get-Content -LiteralPath (Join-Path $directory 'run.json') -Raw | ConvertFrom-Json
    if($manifest.status -ne 'SUCCESS' -or !$manifest.calibration){throw "Not a successful calibration run: $directory"}
    $epochs=Import-Csv -LiteralPath (Join-Path $directory 'global-epoch-history.csv')
    if($epochs.Count -ne 1){throw "Calibration must contain exactly one epoch: $directory"}
    $row=$epochs[0]
    $candidates += [pscustomobject]@{
        runDirectory=($directory -replace '\\','/');trainBatchSize=[int]$manifest.batchSize
        validationBatchSize=[int]$manifest.validationBatchSize
        validationMacroRmse=[double]::Parse($row.validation_macro_rmse,[Globalization.CultureInfo]::InvariantCulture)
        secondsPerEpoch=[double]::Parse($row.train_seconds,[Globalization.CultureInfo]::InvariantCulture)+[double]::Parse($row.validation_seconds,[Globalization.CultureInfo]::InvariantCulture)
    }
}
$baseline=$candidates|Where-Object {$_.trainBatchSize -eq 64 -and $_.validationBatchSize -eq 256}|Select-Object -First 1
if(!$baseline){throw 'Calibration baseline 64/256 is required.'}
$limit=$baseline.validationMacroRmse*(1+$MaxRmseRegressionPercent/100)
$selected=$candidates|Where-Object {$_.validationMacroRmse -le $limit}|Sort-Object secondsPerEpoch,validationMacroRmse,trainBatchSize|Select-Object -First 1
if(!$selected){throw 'No eligible calibration candidate.'}
if(!$OutputPath){$OutputPath=Join-Path 'output' ('global-calibration-selection-'+(Get-Date -Format 'yyyy-MM-dd_HHmmss_fff')+'.json')}
$result=[ordered]@{policy='fastest candidate within baseline RMSE regression threshold';maxRmseRegressionPercent=$MaxRmseRegressionPercent;baseline=$baseline;rmseLimit=$limit;selected=$selected;candidates=$candidates}
$result|ConvertTo-Json -Depth 5|Set-Content -LiteralPath $OutputPath -Encoding utf8
Write-Host "Calibration selected train=$($selected.trainBatchSize), validation=$($selected.validationBatchSize), RMSE=$($selected.validationMacroRmse), sec/epoch=$($selected.secondsPerEpoch)"
Write-Output (Resolve-Path $OutputPath).Path
