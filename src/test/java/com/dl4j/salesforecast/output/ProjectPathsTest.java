package com.dl4j.salesforecast.output;

import com.dl4j.salesforecast.model.ExperimentConfiguration;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;

class ProjectPathsTest {
    @Test
    void routesNewRunsByPurposeWithoutTouchingLegacyRuns(@TempDir Path root) {
        ProjectPaths paths = ProjectPaths.fromRoot(root);

        assertEquals(root.resolve("data/train.csv"), paths.trainingDataset());
        assertEquals(root.resolve("output/training"), paths.runRoot(
                ExperimentConfiguration.fromArgs(new String[]{"--global-store"})));
        assertEquals(root.resolve("output/smoke-tests"), paths.runRoot(
                ExperimentConfiguration.fromArgs(new String[]{"--global-store-smoke"})));
        assertEquals(root.resolve("output/benchmarks/global-runtime"), paths.runRoot(
                ExperimentConfiguration.fromArgs(new String[]{"--global-store-benchmark"})));
        assertEquals(root.resolve("output/benchmarks/global-calibration"), paths.runRoot(
                ExperimentConfiguration.fromArgs(new String[]{"--global-store-calibration"})));
        assertEquals(root.resolve("output/evaluation"), paths.evaluationRoot());
        assertEquals(root.resolve("artifacts"), paths.artifactsRoot());
        assertEquals(root.resolve("output/runs"), paths.legacyRunsRoot());
    }
}
