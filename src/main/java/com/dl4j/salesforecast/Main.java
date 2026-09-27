package com.dl4j.salesforecast;

import com.dl4j.salesforecast.analysis.SalesDataAnalyzer;
import com.dl4j.salesforecast.analysis.TimeSeriesAnalyzer;
import com.dl4j.salesforecast.evaluation.WeeklyNaiveBaseline;
import com.dl4j.salesforecast.model.AutoregressiveLstmForecaster;
import com.dl4j.salesforecast.model.SalesLstmForecaster;
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
        WeeklyNaiveBaseline.BaselineResult validationBaseline =
                WeeklyNaiveBaseline.evaluate(prepared.getValidation());
        WeeklyNaiveBaseline.print(validationBaseline, prepared.getStoreId(), prepared.getItemId(), "VALIDATION");

        SalesLstmForecaster.TuningResult tuning = SalesLstmForecaster.tuneOnValidation(
                prepared, SalesLstmForecaster.DEFAULT_SEARCH_EPOCHS,
                SalesLstmForecaster.DEFAULT_EARLY_STOPPING_PATIENCE);
        SalesLstmForecaster.printTuningSummary(tuning);

        SalesLstmForecaster.FitResult direct = tuning.getBest();
        AutoregressiveLstmForecaster.FitResult autoregressive = AutoregressiveLstmForecaster.fit(
                prepared, SalesLstmForecaster.DEFAULT_SEARCH_EPOCHS,
                direct.getHiddenUnits(), direct.getLearningRate(),
                SalesLstmForecaster.DEFAULT_EARLY_STOPPING_PATIENCE);
        AutoregressiveLstmForecaster.printSummary(autoregressive);
        AutoregressiveLstmForecaster.printComparison(direct.getValidationMetrics(), validationBaseline,
                autoregressive.getValidationMetrics());
    }
}
