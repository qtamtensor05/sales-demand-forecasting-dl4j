package com.dl4j.salesforecast.model;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import com.dl4j.salesforecast.analysis.TimeSeriesAnalyzer;
import com.dl4j.salesforecast.preprocessing.SalesPreprocessor;
import org.nd4j.linalg.dataset.DataSet;
import java.nio.file.Path;
import java.nio.file.Files;
import java.time.LocalDate;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;

class ResidualLstmForecasterTest {
    @Test
    void exposesResidualTargetAndReconstructionWithoutChangingSalesScaler() {
        Class<?> model = assertDoesNotThrow(() -> Class.forName(
                "com.dl4j.salesforecast.model.ResidualLstmForecaster"),
                "Residual forecasting functionality must exist independently of absolute AR");
        assertDoesNotThrow(() -> model.getMethod("fit",
                com.dl4j.salesforecast.preprocessing.SalesPreprocessor.PreprocessingResult.class,
                int.class, int.class, double.class, int.class, long.class, TrainingProgress.class));
    }

    @TempDir Path directory;

    private SalesPreprocessor.PreprocessingResult prepared(String name, int futureOffset,
                                                           int testOffset, boolean constant) throws Exception {
        LocalDate start = LocalDate.of(2020, 1, 1);
        StringBuilder csv = new StringBuilder("date,store,item,sales\n");
        for (int day = 0; day < 105; day++) {
            int sales = constant ? 80 : 80 + (day % 7) * 3 + (day / 7 % 3) * 7;
            if (day >= 71) { sales += futureOffset; }
            if (day >= 85) { sales += testOffset; }
            csv.append(start.plusDays(day)).append(",1,1,").append(sales).append('\n');
        }
        Path file = directory.resolve(name + ".csv");
        Files.writeString(file, csv);
        return SalesPreprocessor.preprocess(TimeSeriesAnalyzer.analyzeQuietly(file.toString(), 1, 1),
                start.plusDays(70), start.plusDays(84), 30, 7);
    }

    @Test
    void calculatesPositiveNegativeZeroResidualAndReconstructsOriginalUnits() throws Exception {
        var p = prepared("arithmetic", 0, 0, false);
        boolean positive = false, negative = false;
        for (var w : p.getTrain().getWindows()) {
            for (int h = 0; h < 7; h++) {
                double anchor = w.getRawInput().get(23 + h);
                double raw = w.getRawTarget().get(h) - anchor;
                positive |= raw > 0;
                negative |= raw < 0;
                double scaled = ResidualLstmForecaster.scaledTarget(w, h, p.getScaler());
                assertEquals(raw, scaled * p.getScaler().getScale(), 1e-10);
                assertEquals(w.getRawTarget().get(h),
                        ResidualLstmForecaster.reconstruct(anchor, scaled, p.getScaler()), 1e-10);
            }
        }
        assertTrue(positive && negative);
        assertEquals(87, ResidualLstmForecaster.reconstruct(80, 7 / p.getScaler().getScale(), p.getScaler()), 1e-10);
        assertEquals(90, ResidualLstmForecaster.reconstruct(100, -10 / p.getScaler().getScale(), p.getScaler()), 1e-10);
        var constant = prepared("constant", 0, 0, true);
        assertEquals(1, constant.getScaler().getScale());
        assertEquals(0, ResidualLstmForecaster.scaledTarget(constant.getTrain().getWindows().get(0), 0, constant.getScaler()));
        assertEquals(80, ResidualLstmForecaster.reconstruct(80, 0, constant.getScaler()));
        assertThrows(IllegalStateException.class, () -> ResidualLstmForecaster.reconstruct(80, Double.NaN, p.getScaler()));
        assertThrows(IllegalStateException.class, () -> ResidualLstmForecaster.reconstruct(80, Double.POSITIVE_INFINITY, p.getScaler()));
        assertThrows(IllegalArgumentException.class, () -> ResidualLstmForecaster.baseline(p.getTrain().getWindows().get(0), 7));
    }

    @Test
    void matchesExplicitPositiveNegativeAndZeroTargetExamples() throws Exception {
        prepared("examples", 0, 0, false);
        Path file = directory.resolve("examples.csv");
        var lines = Files.readAllLines(file);
        LocalDate start = LocalDate.of(2020, 1, 1);
        int[] days = {36, 43, 37, 44, 38, 45};
        int[] sales = {80, 87, 100, 90, 80, 80};
        for (int i = 0; i < days.length; i++) {
            lines.set(days[i] + 1, start.plusDays(days[i]) + ",1,1," + sales[i]);
        }
        Files.write(file, lines);
        var p = SalesPreprocessor.preprocess(TimeSeriesAnalyzer.analyzeQuietly(file.toString(), 1, 1),
                start.plusDays(70), start.plusDays(84), 30, 7);
        var window = p.getTrain().getWindows().get(0);
        assertEquals(7, ResidualLstmForecaster.scaledTarget(window, 0, p.getScaler()) * p.getScaler().getScale(), 1e-10);
        assertEquals(-10, ResidualLstmForecaster.scaledTarget(window, 1, p.getScaler()) * p.getScaler().getScale(), 1e-10);
        assertEquals(0, ResidualLstmForecaster.scaledTarget(window, 2, p.getScaler()));
    }

    @Test
    void futureChangesCannotAffectTrainScalingLabelsOrOriginFeatures() throws Exception {
        var original = prepared("original", 0, 0, false);
        var mutated = prepared("mutated", 10000, 20000, false);
        assertEquals(original.getScaler().getMin(), mutated.getScaler().getMin());
        assertEquals(original.getScaler().getMax(), mutated.getScaler().getMax());
        DataSet a = AutoregressiveLstmForecaster.toOneStepDataSet(original.getTrain().getWindows(), 30, 6, original.getScaler(), true);
        DataSet b = AutoregressiveLstmForecaster.toOneStepDataSet(mutated.getTrain().getWindows(), 30, 6, mutated.getScaler(), true);
        assertEquals(a.getFeatures(), b.getFeatures());
        assertEquals(a.getLabels(), b.getLabels());
        var wa = original.getValidation().getWindows().get(0);
        var wb = mutated.getValidation().getWindows().get(0);
        assertEquals(wa.getInputFeatures(), wb.getInputFeatures());
        for (int h = 0; h < 7; h++) {
            assertEquals(ResidualLstmForecaster.baseline(wa, h), ResidualLstmForecaster.baseline(wb, h));
            assertEquals(10000 / original.getScaler().getScale(),
                    ResidualLstmForecaster.scaledTarget(wb, h, mutated.getScaler())
                    - ResidualLstmForecaster.scaledTarget(wa, h, original.getScaler()), 1e-9);
        }
        assertArrayEquals(new long[]{22, 6, 30}, a.getFeatures().shape());
        assertArrayEquals(new long[]{22, 1, 30}, a.getLabels().shape());
        assertArrayEquals(new long[]{22, 30}, a.getLabelsMaskArray().shape());
        assertEquals(22, a.getLabelsMaskArray().sumNumber().intValue());
        for (int row = 0; row < 22; row++) {
            assertEquals(1, a.getLabelsMaskArray().getDouble(row, 29));
            assertEquals(ResidualLstmForecaster.scaledTarget(original.getTrain().getWindows().get(row), 0, original.getScaler()),
                    a.getLabels().getDouble(row, 0, 29), 1e-6);
        }
        for (var split : List.of(original.getTrain(), original.getValidation(), original.getTest())) {
            for (var w : split.getWindows()) {
                assertFalse(w.getTargetDates().get(0).isBefore(split.getDates().get(0)));
                assertFalse(w.getTargetDates().get(6).isAfter(split.getDates().get(split.getDates().size() - 1)));
            }
        }
    }

    @Test
    void smokeTrainsAndRollsOutWithoutTeacherForcingOrTestSelection() throws Exception {
        var p = prepared("smoke", 0, 0, false);
        var changed = prepared("future", 10000, 20000, false);
        var fit = ResidualLstmForecaster.fit(p, 2, 4, 0.001, 1, 42L, null);
        assertEquals(42L, fit.getRandomSeed());
        assertEquals(7, fit.getValidationMetrics().getHorizons().size());
        assertEquals(56, fit.getValidationMetrics().getForecastCount());
        double best = fit.getHistory().stream().mapToDouble(e -> e.getValidationMetrics().getRmse()).min().orElseThrow();
        assertEquals(best, fit.getValidationMetrics().getRmse(), 1e-5);
        assertTrue(fit.getHistory().stream().allMatch(e -> Double.isFinite(e.getTrainingLoss()) && Double.isFinite(e.getValidationLoss())));
        var other = ResidualLstmForecaster.evaluate(fit.getNetwork(), changed.getValidation(), 30, 7, changed.getScaler());
        var predictions = fit.getValidationMetrics().getPredictions();
        for (int h = 0; h < 7; h++) {
            var a = predictions.get(h * 8);
            var b = other.getPredictions().get(h * 8);
            assertEquals(h + 1, a.getHorizon());
            assertEquals(a.getOriginDate().plusDays(h + 1), a.getTargetDate());
            // For the first origin, ALL future actuals changed, yet all seven predictions are unchanged.
            assertEquals(a.getPrediction(), b.getPrediction(), 1e-5);
            assertNotEquals(a.getActual(), b.getActual());
        }
        double mae = predictions.stream().mapToDouble(r -> Math.abs(r.getActual()-r.getPrediction())).average().orElseThrow();
        double mse = predictions.stream().mapToDouble(r -> Math.pow(r.getActual()-r.getPrediction(), 2)).average().orElseThrow();
        assertEquals(mae, fit.getValidationMetrics().getMae(), 1e-10);
        assertEquals(Math.sqrt(mse), fit.getValidationMetrics().getRmse(), 1e-10);
        // Zero network output must reconstruct exactly Weekly Naive at every horizon.
        fit.getNetwork().params().assign(0);
        var zero = ResidualLstmForecaster.evaluate(fit.getNetwork(), p.getValidation(), 30, 7, p.getScaler());
        assertTrue(zero.getPredictions().stream().allMatch(r -> r.getPrediction() == r.getWeeklyNaive()));
        var testChanged = prepared("testonly", 0, 20000, false);
        var fitAgain = ResidualLstmForecaster.fit(testChanged, 2, 4, 0.001, 1, 42L, null);
        assertEquals(fit.getBestEpoch(), fitAgain.getBestEpoch());
        assertEquals(best, fitAgain.getValidationMetrics().getRmse(), 1e-5);
    }
}
