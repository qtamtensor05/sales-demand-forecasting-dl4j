package com.dl4j.salesforecast;

import com.dl4j.salesforecast.analysis.SalesDataAnalyzer;
import com.dl4j.salesforecast.analysis.TimeSeriesAnalyzer;
import com.dl4j.salesforecast.model.ExperimentRunner;
import com.dl4j.salesforecast.preprocessing.SalesPreprocessor;
import java.io.IOException;
import java.nio.file.Path;

public class Main {

    public static void main(String[] args) throws IOException {

        String trainPath =
                "data/train.csv";

        Path outputPath = Path.of("output", "sales-statistics.txt");
        SalesDataAnalyzer.analyze(trainPath, outputPath);
        System.out.println("Statistics saved to: " + outputPath.toAbsolutePath());
        TimeSeriesAnalyzer.TimeSeriesResult series = TimeSeriesAnalyzer.analyze(trainPath, 1, 1);
        SalesPreprocessor.PreprocessingResult prepared = SalesPreprocessor.preprocess(series);
        SalesPreprocessor.printSummary(prepared);
        ExperimentRunner.ExperimentResult experiments = ExperimentRunner.run(prepared);
        ExperimentRunner.printSummary(experiments);
    }
}
