package com.dl4j.salesforecast.evaluation;

import com.dl4j.salesforecast.analysis.TimeSeriesAnalyzer;
import com.dl4j.salesforecast.preprocessing.SalesPreprocessor;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;

import static org.junit.jupiter.api.Assertions.*;

class WeeklyNaiveBaselineTest {
    @TempDir Path directory;
    private final LocalDate start = LocalDate.of(2020, 1, 1);

    private SalesPreprocessor.PreprocessingResult preprocess(int inputDays, int forecastDays) throws IOException {
        StringBuilder csv = new StringBuilder("date,store,item,sales\n");
        for (int i = 0; i < 42; i++) {
            csv.append(start.plusDays(i)).append(",1,1,").append(i).append('\n');
        }
        Path file = directory.resolve("train.csv");
        Files.writeString(file, csv);
        return SalesPreprocessor.preprocess(TimeSeriesAnalyzer.analyze(file.toString(), 1, 1),
                start.plusDays(25), start.plusDays(35), inputDays, forecastDays);
    }

    @Test
    void usesSalesFromSevenDaysEarlierAndScoresEachHorizonInOriginalUnits() throws IOException {
        WeeklyNaiveBaseline.BaselineResult result = WeeklyNaiveBaseline.evaluate(preprocess(7, 2).getTest());
        assertEquals(2, result.getHorizons().size());
        assertEquals(5, result.getHorizons().get(0).getForecastCount());
        assertEquals(5, result.getHorizons().get(1).getForecastCount());
        assertEquals(10, result.getForecastCount());
        assertEquals(7, result.getHorizons().get(0).getMae());
        assertEquals(7, result.getHorizons().get(0).getRmse());
        assertEquals(7, result.getHorizons().get(1).getMae());
        assertEquals(7, result.getMae());
        assertEquals(7, result.getRmse());
        assertThrows(UnsupportedOperationException.class, () -> result.getHorizons().clear());
    }

    @Test
    void rejectsWindowWithoutSevenDaysOfHistory() throws IOException {
        assertThrows(IllegalArgumentException.class,
                () -> WeeklyNaiveBaseline.evaluate(preprocess(6, 2).getTest()));
    }
}
