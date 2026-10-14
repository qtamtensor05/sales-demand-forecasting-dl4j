Set-StrictMode -Version Latest

function Get-ProjectPaths {
    param([string]$Root = (Split-Path -Parent $PSScriptRoot))
    $resolvedRoot = [IO.Path]::GetFullPath($Root)
    $dataDir = if ($env:DATA_DIR) { [IO.Path]::GetFullPath($env:DATA_DIR, $resolvedRoot) } else { Join-Path $resolvedRoot 'data' }
    $outputDir = if ($env:OUTPUT_DIR) { [IO.Path]::GetFullPath($env:OUTPUT_DIR, $resolvedRoot) } else { Join-Path $resolvedRoot 'output' }
    $artifactsDir = if ($env:ARTIFACTS_DIR) { [IO.Path]::GetFullPath($env:ARTIFACTS_DIR, $resolvedRoot) } else { Join-Path $resolvedRoot 'artifacts' }
    [ordered]@{
        ProjectRoot = $resolvedRoot
        DataDir = $dataDir
        Dataset = Join-Path $dataDir 'train.csv'
        OutputDir = $outputDir
        TrainingDir = Join-Path $outputDir 'training'
        EvaluationDir = Join-Path $outputDir 'evaluation'
        SmokeDir = Join-Path $outputDir 'smoke-tests'
        BenchmarkDir = Join-Path $outputDir 'benchmarks'
        LogDir = Join-Path $outputDir 'logs'
        ArtifactsDir = $artifactsDir
        LegacyRunsDir = Join-Path $outputDir 'runs'
    }
}

function Initialize-ProjectPaths {
    param([System.Collections.IDictionary]$Paths)
    foreach ($path in @($Paths.OutputDir, $Paths.TrainingDir, $Paths.EvaluationDir, $Paths.SmokeDir,
            $Paths.BenchmarkDir, $Paths.LogDir, $Paths.ArtifactsDir)) {
        New-Item -ItemType Directory -Force -Path $path | Out-Null
    }
    $env:PROJECT_ROOT = $Paths.ProjectRoot
    $env:DATA_DIR = $Paths.DataDir
    $env:OUTPUT_DIR = $Paths.OutputDir
    $env:ARTIFACTS_DIR = $Paths.ArtifactsDir
}
