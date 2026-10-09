package com.dl4j.salesforecast.model;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class ErrorAnalysisRunnerTest {
    @Test
    void summarizesMacroMetricsWithSampleStandardDeviation() {
        ErrorAnalysisRunner.MetricSummary summary = ErrorAnalysisRunner.summarize(List.of(
                metrics(10, 2.0, 3.0, 2.0, 3.0),
                metrics(10, 4.0, 5.0, 4.0, 5.0)));

        assertEquals(3.0, summary.getMaeMean(), 1e-12);
        assertEquals(Math.sqrt(2.0), summary.getMaeSampleStandardDeviation(), 1e-12);
        assertEquals(4.0, summary.getRmseMean(), 1e-12);
        assertEquals(Math.sqrt(2.0), summary.getRmseSampleStandardDeviation(), 1e-12);
        assertEquals(2, summary.getSampleCount());
        assertEquals(20, summary.getForecastCount());
    }

    @Test
    void analyzesStrataHorizonsSeedsImprovementsAndBaselineWins() {
        ErrorAnalysisRunner.SeriesInput low = series("LOW", 1,
                metrics(20, 5.0, 10.0, 4.0, 8.0),
                seed(42, metrics(20, 4.0, 8.0, 3.0, 6.0), metrics(20, 4.0, 8.0, 3.0, 6.0)),
                seed(99, metrics(20, 6.0, 12.0, 5.0, 10.0), metrics(20, 4.0, 8.0, 3.0, 6.0)));
        ErrorAnalysisRunner.SeriesInput high = series("HIGH", 2,
                metrics(20, 10.0, 20.0, 8.0, 16.0),
                seed(42, metrics(20, 8.0, 16.0, 6.0, 12.0), metrics(20, 9.0, 18.0, 7.0, 14.0)),
                seed(99, metrics(20, 12.0, 24.0, 10.0, 20.0), metrics(20, 11.0, 22.0, 9.0, 18.0)));

        ErrorAnalysisRunner.AnalysisResult result = ErrorAnalysisRunner.analyze(List.of(low, high));

        assertEquals(2, result.getSeriesCount());
        assertEquals(2, result.getSeedCount());
        assertEquals(2, result.getStrata().size());
        assertEquals(1, result.getHorizons().size());
        assertEquals(2, result.getSeeds().size());
        assertEquals(0, result.getDirectWinsByRmse());
        assertEquals(1, result.getAutoregressiveWinsByRmse());
        assertEquals(0.0, result.getOverallDirectRmseImprovementPercent(), 1e-12);
        assertEquals(100.0 / 15.0, result.getOverallAutoregressiveRmseImprovementPercent(), 1e-12);
        assertTrue(ErrorAnalysisRunner.formatReport(result).contains("Macro aggregation"));
        assertTrue(ErrorAnalysisRunner.formatReport(result).contains("Horizon 1"));
        assertTrue(ErrorAnalysisRunner.formatReport(result).contains("Seed 42"));
        assertTrue(ErrorAnalysisRunner.formatReport(result).contains("RMSE wins vs weekly naive"));
    }

    @Test
    void computesPositiveImprovementWhenModelErrorIsLower() {
        assertEquals(25.0, ErrorAnalysisRunner.improvementPercent(8.0, 6.0), 1e-12);
        assertEquals(-25.0, ErrorAnalysisRunner.improvementPercent(8.0, 10.0), 1e-12);
        assertThrows(IllegalArgumentException.class,
                () -> ErrorAnalysisRunner.improvementPercent(0.0, 1.0));
    }

    private static ErrorAnalysisRunner.SeriesInput series(String stratum, int store,
                                                           ErrorAnalysisRunner.MethodMetrics baseline,
                                                           ErrorAnalysisRunner.SeedInput... seeds) {
        return new ErrorAnalysisRunner.SeriesInput(stratum, store, 1, baseline, List.of(seeds));
    }

    private static ErrorAnalysisRunner.SeedInput seed(long seed, ErrorAnalysisRunner.MethodMetrics direct,
                                                       ErrorAnalysisRunner.MethodMetrics autoregressive) {
        return new ErrorAnalysisRunner.SeedInput(seed, direct, autoregressive);
    }

    private static ErrorAnalysisRunner.MethodMetrics metrics(long count, double mae, double rmse,
                                                              double horizonMae, double horizonRmse) {
        return new ErrorAnalysisRunner.MethodMetrics(count, mae, rmse,
                List.of(new ErrorAnalysisRunner.HorizonMetric(1, count, horizonMae, horizonRmse)));
    }
}
