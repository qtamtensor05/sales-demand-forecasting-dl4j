package com.dl4j.salesforecast.output;

import com.dl4j.salesforecast.model.ExperimentConfiguration;

import java.nio.file.Path;

/** Central path policy for new runs. Legacy output/runs remains readable and is never migrated implicitly. */
public final class ProjectPaths {
    private final Path projectRoot;
    private final Path dataDir;
    private final Path outputDir;
    private final Path artifactsDir;

    private ProjectPaths(Path projectRoot, Path dataDir, Path outputDir, Path artifactsDir) {
        this.projectRoot = projectRoot.toAbsolutePath().normalize();
        this.dataDir = resolve(this.projectRoot, dataDir);
        this.outputDir = resolve(this.projectRoot, outputDir);
        this.artifactsDir = resolve(this.projectRoot, artifactsDir);
    }

    public static ProjectPaths fromEnvironment() {
        Path root = configured("PROJECT_ROOT", Path.of(""));
        return new ProjectPaths(root, configured("DATA_DIR", Path.of("data")),
                configured("OUTPUT_DIR", Path.of("output")),
                configured("ARTIFACTS_DIR", Path.of("artifacts")));
    }

    public static ProjectPaths fromRoot(Path root) {
        return new ProjectPaths(root, Path.of("data"), Path.of("output"), Path.of("artifacts"));
    }

    public Path projectRoot() { return projectRoot; }
    public Path trainingDataset() { return dataDir.resolve("train.csv"); }
    public Path outputRoot() { return outputDir; }
    public Path artifactsRoot() { return artifactsDir; }
    public Path evaluationRoot() { return outputDir.resolve("evaluation"); }
    public Path logsRoot() { return outputDir.resolve("logs"); }
    public Path legacyRunsRoot() { return outputDir.resolve("runs"); }

    public Path runRoot(ExperimentConfiguration configuration) {
        if (configuration.isSmoke()) { return outputDir.resolve("smoke-tests"); }
        if (configuration.isCalibration()) { return outputDir.resolve("benchmarks/global-calibration"); }
        if (configuration.isBenchmark()) { return outputDir.resolve("benchmarks/global-runtime"); }
        return outputDir.resolve("training");
    }

    public String runType(ExperimentConfiguration configuration) {
        if (configuration.isSmoke()) { return "SMOKE_TEST"; }
        if (configuration.isCalibration()) { return "CALIBRATION_BENCHMARK"; }
        if (configuration.isBenchmark()) { return "RUNTIME_BENCHMARK"; }
        return "TRAINING";
    }

    private static Path configured(String name, Path fallback) {
        String property = System.getProperty("sales." + name.toLowerCase().replace('_', '.'));
        String environment = System.getenv(name);
        String value = property != null && !property.isBlank() ? property
                : environment != null && !environment.isBlank() ? environment : null;
        return value == null ? fallback : Path.of(value);
    }

    private static Path resolve(Path root, Path value) {
        return value.isAbsolute() ? value.toAbsolutePath().normalize() : root.resolve(value).normalize();
    }
}
