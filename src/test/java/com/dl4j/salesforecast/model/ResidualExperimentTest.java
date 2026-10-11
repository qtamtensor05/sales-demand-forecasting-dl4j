package com.dl4j.salesforecast.model;

import com.dl4j.salesforecast.analysis.TimeSeriesAnalyzer;
import com.dl4j.salesforecast.preprocessing.SalesPreprocessor;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;

class ResidualExperimentTest {
    @Test
    void preservesLegacyConfigurationAndRequiresExplicitResidualOptIn() {
        var legacy = ExperimentConfiguration.fromArgs(new String[0]);
        assertEquals(120, legacy.getFitCount());
        assertFalse(legacy.includesResidual());
        var full = ExperimentConfiguration.fromArgs(new String[]{"--residual"});
        assertEquals(180, full.getFitCount());
        assertEquals(ExperimentRunner.DEFAULT_SEEDS, full.getSeeds());
        assertEquals(30, full.getMaxEpochs());
        assertEquals(5, full.getPatience());
        var smoke = ExperimentConfiguration.fromArgs(new String[]{"--residual-smoke"});
        assertEquals(3, smoke.getFitCount());
        assertEquals(2, smoke.getMaxEpochs());
        assertThrows(IllegalArgumentException.class, () -> ExperimentConfiguration.fromArgs(new String[]{"--typo"}));
        assertTrue(TrainingProgress.forFit(11, 12, 4, 5, 2, full.getModels(), "HIGH", 10, 28, 99, 30)
                .formatCompletedEpoch(1).startsWith("[180/180] ResidualAR"));
    }

    @Test
    void reportsMacroSeedStratumHorizonAndStrictSeriesWins() {
        List<ResidualComparisonReport.Row> rows = new ArrayList<>();
        for (int h = 0; h <= 7; h++) {
            for (long seed : List.of(42L, 99L)) {
                rows.add(new ResidualComparisonReport.Row("LOW", 1, 1, seed, h,
                        new double[]{10, 11, 12, seed == 42 ? 5 : 15},
                        new double[]{10, 11, 12, seed == 42 ? 5 : 15}));
                rows.add(new ResidualComparisonReport.Row("HIGH", 2, 1, seed, h,
                        new double[]{20, 21, 22, 18}, new double[]{20, 21, 22, 18}));
            }
        }
        String report = ResidualComparisonReport.formatRows(rows);
        assertTrue(report.contains("ResidualAR | 14.0000 +/- 5.6569 | 14.0000 +/- 5.6569 | +6.67 | +6.67"));
        assertTrue(report.contains("Weekly 1/2; Direct 2/2; AR 2/2;"));
        assertTrue(report.contains("STRATUM HIGH"));
        assertTrue(report.contains("HORIZON 7"));
        assertTrue(report.contains("SEED 99"));
        assertTrue(report.contains("ResidualAR | MAE std 3.5355 | RMSE std 3.5355"));
    }

    @Test
    void runnerExportsAlignedResidualEvidenceAndRetainsOldColumns(@TempDir Path directory) throws Exception {
        LocalDate start = LocalDate.of(2020, 1, 1);
        StringBuilder csv = new StringBuilder("date,store,item,sales\n");
        for (int d = 0; d < 100; d++) {
            csv.append(start.plusDays(d)).append(",1,1,").append(80 + d % 7 + d / 7).append('\n');
        }
        Path file = directory.resolve("fixture.csv");
        Files.writeString(file, csv);
        var prepared = SalesPreprocessor.preprocess(TimeSeriesAnalyzer.analyzeQuietly(file.toString(), 1, 1),
                start.plusDays(70), start.plusDays(84), 30, 7);
        var experiment = ExperimentRunner.run(prepared, List.of(42L), 0, 1, "LOW", 1, 1, ExperimentConfiguration.smoke());
        List<MultiSeriesExperimentRunner.SeriesProfile> profiles = List.of(
                new MultiSeriesExperimentRunner.SeriesProfile(1, 1, 80),
                new MultiSeriesExperimentRunner.SeriesProfile(2, 1, 100),
                new MultiSeriesExperimentRunner.SeriesProfile(3, 1, 120));
        var selection = MultiSeriesExperimentRunner.selectRepresentativeSeries(profiles, 1).get(0);
        var series = new MultiSeriesExperimentRunner.SeriesResult(selection, experiment.getWeeklyMetrics(),
                experiment.getSeedResults(), prepared.getScaler());
        var result = new MultiSeriesExperimentRunner.MultiSeriesResult(List.of(42L), List.of(series));
        ValidationPredictionExporter.writeCsv(result, directory.resolve("predictions.csv"));
        SeedMetricsExporter.writeCsv(result, directory.resolve("seeds.csv"));
        TrainingHistoryExporter.writeCsv(result, directory.resolve("history.csv"));
        var predictions = Files.readAllLines(directory.resolve("predictions.csv"));
        assertEquals(169, predictions.size()); // 8 origins x 7 horizons x 3 models, plus header.
        assertEquals("stratum,store,item,model,seed,origin_date,target_date,horizon,actual,prediction,weekly_naive,predicted_residual", predictions.get(0));
        for (String line : predictions.subList(1, predictions.size())) {
            String[] fields = line.split(",", -1);
            assertEquals(12, fields.length);
            if (fields[3].equals("ResidualAR")) {
                assertEquals(Double.parseDouble(fields[9]), Double.parseDouble(fields[10]) + Double.parseDouble(fields[11]), 2e-10);
            } else { assertEquals("", fields[11]); }
        }
        var seedLines = Files.readAllLines(directory.resolve("seeds.csv"));
        assertEquals(16, seedLines.get(0).split(",").length);
        assertEquals(16, seedLines.get(1).split(",").length);
        var history = Files.readAllLines(directory.resolve("history.csv"));
        assertEquals(3, history.stream().filter(l -> l.endsWith(",true")).count());
        assertTrue(history.stream().anyMatch(l -> l.contains(",ResidualAR,")));
        assertTrue(ResidualComparisonReport.format(result).contains("OVERALL MACRO"));
    }
}
