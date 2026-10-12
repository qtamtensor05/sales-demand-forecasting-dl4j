package com.dl4j.salesforecast;

import com.dl4j.salesforecast.analysis.SalesDataAnalyzer;
import com.dl4j.salesforecast.model.MultiSeriesExperimentRunner;
import com.dl4j.salesforecast.model.ErrorAnalysisRunner;
import com.dl4j.salesforecast.model.ExperimentConfiguration;
import com.dl4j.salesforecast.model.ResidualComparisonReport;
import com.dl4j.salesforecast.output.RunArtifactSession;
import java.nio.file.Path;

public class Main {

    public static void main(String[] args) throws Throwable {

        ExperimentConfiguration configuration = ExperimentConfiguration.fromArgs(args);
        String trainPath =
                "data/train.csv";

        if(configuration.isPreflight()) {
            var check=com.dl4j.salesforecast.model.GlobalPreflight.run(Path.of(trainPath),Path.of("output","runs"),configuration);
            System.out.printf("GLOBAL PREFLIGHT PASS%nJava: %s%nBackend: %s%nStore: %d%nItems: %d%nSeeds: %s%nTrain batch: %d%nValidation batch: %d%nDimensions: [batch,56,30] -> H1-H7%nTrain windows: %d%nValidation origins: %d%nForecasts/seed: %d%n",
                    System.getProperty("java.version"),check.backend,configuration.getStoreId(),configuration.getItemIds().size(),configuration.getSeeds(),configuration.getBatchSize(),configuration.getValidationBatchSize(),check.trainWindows,check.validationOrigins,check.forecastsPerSeed);
            if(!check.gpu) System.out.println("WARNING: CPU backend detected. Do not start intended GPU training until CUDA backend is active.");
            return;
        }

        RunArtifactSession artifacts = RunArtifactSession.start(
                Path.of("output", "runs"), Path.of(trainPath), configuration);
        System.out.println("Run artifacts: " + artifacts.getDirectory().toAbsolutePath());
        try {
            if (configuration.isGlobalStore()) {
                var global = configuration.isCalibration()
                        ? com.dl4j.salesforecast.model.GlobalExperimentRunner.run(trainPath, configuration)
                        : com.dl4j.salesforecast.model.GlobalExperimentRunner.run(trainPath, configuration, artifacts.getDirectory());
                artifacts.complete(global);
                System.out.print(com.dl4j.salesforecast.model.GlobalExperimentReport.format(global, configuration));
                System.out.println("Run completed: " + artifacts.getDirectory().toAbsolutePath());
                return;
            }
            Path statisticsPath = artifacts.getDirectory().resolve("dataset-statistics.txt");
            SalesDataAnalyzer.analyze(trainPath, statisticsPath);
            MultiSeriesExperimentRunner.MultiSeriesResult experiments = MultiSeriesExperimentRunner.run(trainPath, configuration);
            ErrorAnalysisRunner.AnalysisResult analysis = configuration.isHuberComparison() ? null : ErrorAnalysisRunner.analyze(experiments);
            artifacts.complete(experiments, analysis);
            if (!configuration.isHuberComparison()) { System.out.print(MultiSeriesExperimentRunner.formatReport(experiments)); }
            if (configuration.isSmoke()) { System.out.println("SMOKE ONLY: not model-quality evidence."); }
            System.out.print(configuration.includesResidual() ? ResidualComparisonReport.format(experiments)
                    : ErrorAnalysisRunner.formatReport(analysis));
            System.out.println("Run completed: " + artifacts.getDirectory().toAbsolutePath());
        } catch (Throwable failure) {
            artifacts.fail(failure);
            throw failure;
        } finally {
            artifacts.close();
        }
    }
}
