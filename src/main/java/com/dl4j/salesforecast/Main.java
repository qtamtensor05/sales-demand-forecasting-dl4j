package com.dl4j.salesforecast;

import com.dl4j.salesforecast.analysis.SalesDataAnalyzer;
import com.dl4j.salesforecast.model.MultiSeriesExperimentRunner;
import com.dl4j.salesforecast.model.ErrorAnalysisRunner;
import com.dl4j.salesforecast.output.RunArtifactSession;
import java.nio.file.Path;

public class Main {

    public static void main(String[] args) throws Throwable {

        String trainPath =
                "data/train.csv";

        RunArtifactSession artifacts = RunArtifactSession.start(
                Path.of("output", "runs"), Path.of(trainPath));
        System.out.println("Run artifacts: " + artifacts.getDirectory().toAbsolutePath());
        try {
            Path statisticsPath = artifacts.getDirectory().resolve("dataset-statistics.txt");
            SalesDataAnalyzer.analyze(trainPath, statisticsPath);
            MultiSeriesExperimentRunner.MultiSeriesResult experiments = MultiSeriesExperimentRunner.run(trainPath);
            ErrorAnalysisRunner.AnalysisResult analysis = ErrorAnalysisRunner.analyze(experiments);
            artifacts.complete(experiments, analysis);
            System.out.print(MultiSeriesExperimentRunner.formatReport(experiments));
            System.out.print(ErrorAnalysisRunner.formatReport(analysis));
            System.out.println("Run completed: " + artifacts.getDirectory().toAbsolutePath());
        } catch (Throwable failure) {
            artifacts.fail(failure);
            throw failure;
        } finally {
            artifacts.close();
        }
    }
}
