package com.dl4j.salesforecast;

import com.dl4j.salesforecast.analysis.SalesDataAnalyzer;
import com.dl4j.salesforecast.model.MultiSeriesExperimentRunner;
import com.dl4j.salesforecast.model.ErrorAnalysisRunner;
import com.dl4j.salesforecast.model.TrainingHistoryExporter;
import java.io.IOException;
import java.nio.file.Path;

public class Main {

    public static void main(String[] args) throws IOException {

        String trainPath =
                "data/train.csv";

        Path outputPath = Path.of("output", "sales-statistics.txt");
        SalesDataAnalyzer.analyze(trainPath, outputPath);
        System.out.println("Statistics saved to: " + outputPath.toAbsolutePath());
        MultiSeriesExperimentRunner.MultiSeriesResult experiments = MultiSeriesExperimentRunner.run(trainPath);
        Path experimentPath = Path.of("output", "multi-series-validation.txt");
        MultiSeriesExperimentRunner.writeReport(experiments, experimentPath);
        System.out.println("Multi-series validation report saved to: " + experimentPath.toAbsolutePath());
        Path historyPath = Path.of("output", "training-history-validation.csv");
        TrainingHistoryExporter.writeCsv(experiments, historyPath);
        System.out.println("Training history saved to: " + historyPath.toAbsolutePath());
        System.out.print(MultiSeriesExperimentRunner.formatReport(experiments));
        ErrorAnalysisRunner.AnalysisResult analysis = ErrorAnalysisRunner.analyze(experiments);
        Path analysisPath = Path.of("output", "error-analysis-validation.txt");
        ErrorAnalysisRunner.writeReport(analysis, analysisPath);
        System.out.println("Validation error analysis saved to: " + analysisPath.toAbsolutePath());
        System.out.print(ErrorAnalysisRunner.formatReport(analysis));
    }
}
