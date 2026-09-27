package com.dl4j.salesforecast.model;

import com.dl4j.salesforecast.analysis.TimeSeriesAnalyzer;
import com.dl4j.salesforecast.preprocessing.SalesPreprocessor;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.nd4j.linalg.api.ndarray.INDArray;
import org.nd4j.linalg.factory.Nd4j;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;

import static org.junit.jupiter.api.Assertions.*;

class SalesLstmForecasterTest {
    @TempDir Path directory;

    @Test
    void trainsSmallNetworkAndPredictsOneVectorForSevenDayHorizon() throws IOException {
        LocalDate start = LocalDate.of(2020, 1, 1);
        StringBuilder csv = new StringBuilder("date,store,item,sales\n");
        for (int day = 0; day < 120; day++) {
            int sales = 10 + day % 7 + day / 7;
            csv.append(start.plusDays(day)).append(",1,1,").append(sales).append('\n');
        }
        Path file = directory.resolve("train.csv");
        Files.writeString(file, csv);
        SalesPreprocessor.PreprocessingResult prepared = SalesPreprocessor.preprocess(
                TimeSeriesAnalyzer.analyze(file.toString(), 1, 1),
                start.plusDays(70), start.plusDays(84), 30, 7);

        SalesLstmForecaster.TuningResult tuning = SalesLstmForecaster.tuneOnValidation(prepared, 2, 1);
        assertEquals(3, tuning.getCandidates().size());
        assertTrue(tuning.getCandidates().stream().allMatch(candidate -> candidate.getEpochsRun() <= 2));
        assertSame(tuning.getBest(), tuning.getCandidates().stream()
                .min(java.util.Comparator.comparingDouble(SalesLstmForecaster.FitResult::getBestValidationRmse))
                .orElseThrow());
        SalesLstmForecaster.TrainingResult trained = SalesLstmForecaster.evaluateTest(tuning.getBest());

        assertTrue(trained.getEpochsRun() >= 1 && trained.getEpochsRun() <= 2);
        assertTrue(trained.getBestEpoch() >= 1 && trained.getBestEpoch() <= trained.getEpochsRun());
        assertTrue(Double.isFinite(trained.getBestValidationRmse()));
        assertEquals(trained.getEpochsRun(), trained.getHistory().size());
        assertEquals(7, trained.getTestMetrics().getHorizons().size());
        assertEquals(29L * 7, trained.getTestMetrics().getForecastCount());

        SalesPreprocessor.Window testWindow = prepared.getTest().getWindows().get(0);
        INDArray input = Nd4j.zeros(1, 1, 30);
        for (int t = 0; t < 30; t++) {
            input.putScalar(new long[]{0, 0, t}, testWindow.getInput().get(t));
        }
        INDArray sequenceOutput = trained.getNetwork().output(input, false);
        assertArrayEquals(new long[]{1, 7, 30}, sequenceOutput.shape());
        for (int horizon = 0; horizon < 7; horizon++) {
            double scaledPrediction = sequenceOutput.getDouble(0, horizon, 29);
            double prediction = prepared.getScaler().inverseTransform(scaledPrediction);
            assertTrue(Double.isFinite(prediction));
        }
    }
}
