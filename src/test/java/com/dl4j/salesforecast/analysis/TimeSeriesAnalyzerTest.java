package com.dl4j.salesforecast.analysis;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class TimeSeriesAnalyzerTest {
    @TempDir
    Path directory;

    private TimeSeriesAnalyzer.TimeSeriesResult analyze(String rows) throws IOException {
        Path csv = directory.resolve("train.csv");
        Files.writeString(csv, "date,store,item,sales\n" + rows);
        return TimeSeriesAnalyzer.analyze(csv.toString(), 1, 1);
    }

    @Test
    void filtersSortsAndComputesPopulationStatistics() throws IOException {
        TimeSeriesAnalyzer.TimeSeriesResult result = analyze(
                "2020-01-04,1,1,6\n2020-01-02,1,1,2\n"
                        + "2020-01-01,1,1,0\n2020-01-03,1,1,4\n"
                        + "2020-01-01,2,1,999\n2020-01-01,1,2,999\n");
        assertEquals(List.of(0.0, 2.0, 4.0, 6.0), result.getSales());
        assertEquals(LocalDate.of(2020, 1, 1), result.getStartDate());
        assertEquals(LocalDate.of(2020, 1, 4), result.getEndDate());
        assertEquals(12, result.getTotalSales());
        assertEquals(0, result.getMin());
        assertEquals(6, result.getMax());
        assertEquals(3, result.getMean());
        assertEquals(3, result.getMedian());
        assertEquals(Math.sqrt(5), result.getStandardDeviation(), 1e-12);
        assertEquals(1, result.getZeroSalesDays());
        assertEquals(4, result.getTotalDays());
        assertEquals(0, result.getMissingDays());
        assertTrue(result.isComplete());
        assertThrows(UnsupportedOperationException.class, () -> result.getSales().add(1.0));
        assertThrows(UnsupportedOperationException.class, () -> result.getDates().clear());
    }

    @Test
    void detectsGapsAndDuplicatesWithoutOverwritingRecords() throws IOException {
        TimeSeriesAnalyzer.TimeSeriesResult result = analyze(
                "2020-01-04,1,1,8\n2020-01-01,1,1,0\n"
                        + "2020-01-01,1,1,0\n2020-01-01,1,1,2\n2020-01-06,1,1,10\n");
        assertEquals(5, result.getSales().size());
        assertEquals(3, result.getTotalDays());
        assertEquals(3, result.getMissingDays());
        assertEquals(2, result.getGapCount());
        assertEquals(1, result.getDuplicateDates());
        assertEquals(2, result.getDuplicateRecords());
        assertEquals(1, result.getZeroSalesDays());
        assertEquals(2, result.getMedian());
        assertEquals(20, result.getTotalSales());
        assertFalse(result.isComplete());
    }

    @Test
    void handlesSingleDayAndLeapDay() throws IOException {
        TimeSeriesAnalyzer.TimeSeriesResult single = analyze("2020-02-29,1,1,7\n");
        assertEquals(7, single.getMedian());
        assertEquals(0, single.getStandardDeviation());
        assertTrue(single.isComplete());
        assertTrue(analyze("2020-02-28,1,1,1\n2020-02-29,1,1,2\n2020-03-01,1,1,3\n").isComplete());
    }

    @Test
    void rejectsMissingSeriesAndInvalidInput() throws IOException {
        assertThrows(IllegalArgumentException.class, () -> analyze(""));
        assertThrows(IllegalArgumentException.class, () -> analyze("2020-01-01,2,1,4\n"));
        assertThrows(IllegalArgumentException.class, () -> analyze("2020-02-30,1,1,4\n"));
        assertThrows(IllegalArgumentException.class, () -> analyze("2020-01-01,1,1,-1\n"));
        assertThrows(IllegalArgumentException.class,
                () -> TimeSeriesAnalyzer.analyze("unused.csv", 0, 1));
        Path missingHeader = directory.resolve("invalid.csv");
        Files.writeString(missingHeader, "date,store,item\n2020-01-01,1,1\n");
        assertThrows(IllegalArgumentException.class,
                () -> TimeSeriesAnalyzer.analyze(missingHeader.toString(), 1, 1));
    }
}
