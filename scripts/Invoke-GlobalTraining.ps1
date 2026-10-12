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
    & (Join-Path $PSScriptRoot 'Invoke-GlobalRun.ps1') -Mode Preflight
    exit $LASTEXITCODE
}
& (Join-Path $PSScriptRoot 'Invoke-GlobalRun.ps1') -Mode Full
exit $LASTEXITCODE
