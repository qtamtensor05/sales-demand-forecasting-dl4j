package com.dl4j.salesforecast.preprocessing;

import com.dl4j.salesforecast.analysis.TimeSeriesAnalyzer;
import com.dl4j.salesforecast.analysis.TimeSeriesAnalyzer.TimeSeriesResult;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class SalesPreprocessorTest {
    @TempDir Path directory;
    private final LocalDate start = LocalDate.of(2020, 1, 1);

    private TimeSeriesResult series(boolean constant) throws IOException {
        StringBuilder csv = new StringBuilder("date,store,item,sales\n");
        for (int i = 0; i < 50; i++) {
            int sales = constant ? 5 : i;
            csv.append(start.plusDays(i)).append(",1,1,").append(sales).append('\n');
        }
        Path file = directory.resolve("train.csv");
        Files.writeString(file, csv);
        return TimeSeriesAnalyzer.analyze(file.toString(), 1, 1);
    }

    @Test
    void splitsTargetsAndUsesOnlyPastInputsWithTrainOnlyScaling() throws IOException {
        SalesPreprocessor.PreprocessingResult result = SalesPreprocessor.preprocess(
                series(false), start.plusDays(24), start.plusDays(34), 3, 2);
        assertEquals(8, result.getTrain().getWindows().size());
        assertEquals(9, result.getValidation().getWindows().size());
        assertEquals(14, result.getTest().getWindows().size());
        assertEquals(0, result.getScaler().getMin());
        assertEquals(24, result.getScaler().getMax());
        assertEquals(35.0 / 24, result.getTest().getNormalizedSales().get(0), 1e-12);
        SalesPreprocessor.Window first = result.getValidation().getWindows().get(0);
        assertEquals(List.of(22.0, 23.0, 24.0), first.getRawInput());
        assertEquals(List.of(25.0, 26.0), first.getRawTarget());
        for (SalesPreprocessor.Split split : List.of(result.getTrain(), result.getValidation(), result.getTest())) {
            for (SalesPreprocessor.Window window : split.getWindows()) {
                assertEquals(3, window.getInput().size());
                assertEquals(3, window.getInputFeatures().size());
                assertEquals(2, window.getTarget().size());
                assertTrue(split.getDates().containsAll(window.getTargetDates()));
                assertEquals(window.getInputDates().get(2).plusDays(1), window.getTargetDates().get(0));
                for (int i = 0; i < window.getTarget().size(); i++) {
                    assertEquals(window.getRawTarget().get(i),
                            result.getScaler().inverseTransform(window.getTarget().get(i)), 1e-12);
                }
            }
        }
        assertEquals(List.of("sales", "sales_lag_7", "rolling_mean_7", "rolling_mean_14",
                        "day_of_week_sin", "day_of_week_cos"), SalesPreprocessor.getInputFeatureNames());
        List<Double> lastInputFeatures = result.getTrain().getWindows().get(0).getInputFeatures().get(2);
        assertEquals(15.0 / 24, lastInputFeatures.get(0), 1e-12);
        assertEquals(8.0 / 24, lastInputFeatures.get(1), 1e-12);
        assertEquals(12.0 / 24, lastInputFeatures.get(2), 1e-12);
        assertEquals(8.5 / 24, lastInputFeatures.get(3), 1e-12);
        assertEquals(Math.sin(2.0 * Math.PI * 3 / 7), lastInputFeatures.get(4), 1e-12);
        assertEquals(Math.cos(2.0 * Math.PI * 3 / 7), lastInputFeatures.get(5), 1e-12);
        assertThrows(UnsupportedOperationException.class, () -> first.getInput().clear());
        assertThrows(UnsupportedOperationException.class, () -> result.getTrain().getWindows().clear());
    }

    @Test
    void constantTrainingSeriesHasFiniteReversibleScaling() throws IOException {
        SalesPreprocessor.PreprocessingResult result = SalesPreprocessor.preprocess(
                series(true), start.plusDays(24), start.plusDays(34), 3, 2);
        assertEquals(1, result.getScaler().getScale());
        assertTrue(result.getTrain().getNormalizedSales().stream().allMatch(value -> value == 0));
        assertEquals(0, result.getValidation().getNormalizedSales().get(0));
        assertEquals(5, result.getScaler().inverseTransform(0));
        assertTrue(result.getTrain().getWindows().get(0).getInputFeatures().stream()
                .flatMap(List::stream).allMatch(Double::isFinite));
    }

    @Test
    void rejectsInvalidBoundariesLengthsAndIncompleteSeries() throws IOException {
        TimeSeriesResult series = series(false);
        assertThrows(IllegalArgumentException.class, () -> SalesPreprocessor.preprocess(
                series, start.plusDays(24), start.plusDays(34), 0, 2));
        assertThrows(IllegalArgumentException.class, () -> SalesPreprocessor.preprocess(
                series, start.plusDays(34), start.plusDays(24), 3, 2));
        assertThrows(IllegalArgumentException.class, () -> SalesPreprocessor.preprocess(
                series, start.plusDays(24), start.plusDays(49), 3, 2));
        assertThrows(IllegalArgumentException.class, () -> SalesPreprocessor.preprocess(
                series, start.plusDays(7), start.plusDays(11), 7, 2));
        assertThrows(IllegalArgumentException.class, () -> SalesPreprocessor.preprocess(
                series, start.plusDays(24), start.plusDays(34), 3, 20));
        Path file = directory.resolve("bad.csv");
        for (String rows : List.of("2020-01-01,1,1,1\n2020-01-03,1,1,2\n",
                "2020-01-01,1,1,1\n2020-01-01,1,1,2\n")) {
            Files.writeString(file, "date,store,item,sales\n" + rows);
            TimeSeriesResult bad = TimeSeriesAnalyzer.analyze(file.toString(), 1, 1);
            assertThrows(IllegalArgumentException.class, () -> SalesPreprocessor.preprocess(bad));
        }
    }

    @Test
    void defaultCalendarSplitHandlesLeapYearAndThirtyToSevenWindows() throws IOException {
        LocalDate first = LocalDate.of(2013, 1, 1);
        LocalDate end = LocalDate.of(2017, 12, 31);
        StringBuilder csv = new StringBuilder("date,store,item,sales\n");
        for (LocalDate date = first; !date.isAfter(end); date = date.plusDays(1)) {
            csv.append(date).append(",1,1,10\n");
        }
        Path file = directory.resolve("calendar.csv");
        Files.writeString(file, csv);
        SalesPreprocessor.PreprocessingResult result = SalesPreprocessor.preprocess(
                TimeSeriesAnalyzer.analyze(file.toString(), 1, 1));
        assertEquals(1095, result.getTrain().getDates().size());
        assertEquals(366, result.getValidation().getDates().size());
        assertEquals(365, result.getTest().getDates().size());
        assertEquals(1046, result.getTrain().getWindows().size());
        assertEquals(360, result.getValidation().getWindows().size());
        assertEquals(359, result.getTest().getWindows().size());
        assertEquals(LocalDate.of(2016, 1, 1), result.getValidation().getWindows().get(0).getTargetDates().get(0));
        List<SalesPreprocessor.Window> windows = result.getTest().getWindows();
        assertEquals(end, windows.get(windows.size() - 1).getTargetDates().get(6));
    }
}
