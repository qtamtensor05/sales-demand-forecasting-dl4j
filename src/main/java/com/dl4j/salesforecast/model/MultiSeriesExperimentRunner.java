package com.dl4j.salesforecast.model;

import com.dl4j.salesforecast.analysis.TimeSeriesAnalyzer;
import com.dl4j.salesforecast.analysis.TimeSeriesAnalyzer.TimeSeriesResult;
import com.dl4j.salesforecast.evaluation.WeeklyNaiveBaseline;
import com.dl4j.salesforecast.preprocessing.SalesPreprocessor;
import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVParser;
import org.apache.commons.csv.CSVRecord;

import java.io.IOException;
import java.io.Reader;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/** Runs the same validation experiment on representative low/medium/high sales series. */
public final class MultiSeriesExperimentRunner {
    public static final int DEFAULT_SAMPLES_PER_STRATUM = 4;
    private static final LocalDate TRAIN_START = LocalDate.of(2013, 1, 1);
    private static final LocalDate TRAIN_END = LocalDate.of(2015, 12, 31);
    private static final long EXPECTED_TRAIN_DAYS = 1095;

    private MultiSeriesExperimentRunner() { }

    public static MultiSeriesResult run(String filePath) {
        return run(filePath, ExperimentRunner.DEFAULT_SEEDS, DEFAULT_SAMPLES_PER_STRATUM);
    }

    public static MultiSeriesResult run(String filePath, List<Long> seeds, int samplesPerStratum) {
        if (filePath == null || filePath.isBlank()) { throw new IllegalArgumentException("Dataset path is required."); }
        if (seeds == null || seeds.isEmpty() || seeds.stream().anyMatch(seed -> seed == null)
                || seeds.stream().distinct().count() != seeds.size()) {
            throw new IllegalArgumentException("Seeds must be non-null, non-empty, and unique.");
        }
        List<SeriesProfile> profiles = readTrainingProfiles(filePath);
        List<SelectedSeries> selected = selectRepresentativeSeries(profiles, samplesPerStratum);
        List<SeriesResult> results = new ArrayList<>(selected.size());
        for (SelectedSeries selection : selected) {
            SeriesProfile profile = selection.profile;
            System.out.printf(Locale.ROOT, "%n=== %s store=%d item=%d, train mean sales=%.4f ===%n",
                    selection.stratum, profile.storeId, profile.itemId, profile.trainMeanSales);
            TimeSeriesResult series = TimeSeriesAnalyzer.analyzeQuietly(filePath, profile.storeId, profile.itemId);
            SalesPreprocessor.PreprocessingResult prepared = SalesPreprocessor.preprocess(series);
            ExperimentRunner.ExperimentResult experiments = ExperimentRunner.run(prepared, seeds);
            results.add(new SeriesResult(selection, experiments.getWeeklyMetrics(), experiments.getSeedResults()));
        }
        return new MultiSeriesResult(seeds, results);
    }

    /** Selects evenly spaced quantiles within tertiles ranked by train-period mean sales. */
    public static List<SelectedSeries> selectRepresentativeSeries(List<SeriesProfile> profiles,
                                                                   int samplesPerStratum) {
        if (profiles == null || profiles.size() < 3 || samplesPerStratum <= 0) {
            throw new IllegalArgumentException("At least three series and a positive sample count are required.");
        }
        List<SeriesProfile> sorted = new ArrayList<>(profiles);
        sorted.sort(Comparator.comparingDouble(SeriesProfile::getTrainMeanSales)
                .thenComparingInt(SeriesProfile::getStoreId).thenComparingInt(SeriesProfile::getItemId));
        if (sorted.stream().map(profile -> profile.storeId + ":" + profile.itemId).distinct().count() != sorted.size()) {
            throw new IllegalArgumentException("Series profiles must have unique store-item IDs.");
        }
        List<SelectedSeries> selections = new ArrayList<>(samplesPerStratum * 3);
        String[] strata = {"LOW", "MEDIUM", "HIGH"};
        int size = sorted.size();
        for (int stratum = 0; stratum < 3; stratum++) {
            int from = stratum * size / 3;
            int to = (stratum + 1) * size / 3;
            List<SeriesProfile> tier = sorted.subList(from, to);
            if (tier.size() < samplesPerStratum) {
                throw new IllegalArgumentException("Not enough profiles in " + strata[stratum] + " sales stratum.");
            }
            for (int sample = 0; sample < samplesPerStratum; sample++) {
                int index = (int) Math.floor((sample + 0.5) * tier.size() / samplesPerStratum);
                selections.add(new SelectedSeries(strata[stratum], tier.get(index)));
            }
        }
        return List.copyOf(selections);
    }

    private static List<SeriesProfile> readTrainingProfiles(String filePath) {
        Map<SeriesKey, ProfileAccumulator> accumulators = new HashMap<>();
        try (Reader reader = Files.newBufferedReader(Path.of(filePath), StandardCharsets.UTF_8);
             CSVParser parser = CSVFormat.DEFAULT.builder().setHeader().setSkipHeaderRecord(true).build().parse(reader)) {
            for (String column : List.of("date", "store", "item", "sales")) {
                if (!parser.getHeaderMap().containsKey(column)) {
                    throw new IllegalArgumentException("Missing CSV column: " + column);
                }
            }
            for (CSVRecord record : parser) {
                try {
                    LocalDate date = LocalDate.parse(record.get("date").trim());
                    int storeId = Integer.parseInt(record.get("store").trim());
                    int itemId = Integer.parseInt(record.get("item").trim());
                    int sales = Integer.parseInt(record.get("sales").trim());
                    if (storeId <= 0 || itemId <= 0 || sales < 0) {
                        throw new IllegalArgumentException("IDs must be positive and sales non-negative.");
                    }
                    if (!date.isBefore(TRAIN_START) && !date.isAfter(TRAIN_END)) {
                        accumulators.computeIfAbsent(new SeriesKey(storeId, itemId),
                                key -> new ProfileAccumulator(key.storeId, key.itemId)).add(date, sales);
                    }
                } catch (RuntimeException e) {
                    throw new IllegalArgumentException("Invalid CSV record " + record.getRecordNumber()
                            + ": " + e.getMessage(), e);
                }
            }
        } catch (IOException e) {
            throw new UncheckedIOException("Cannot read dataset: " + filePath, e);
        }

        List<SeriesProfile> profiles = new ArrayList<>();
        for (ProfileAccumulator accumulator : accumulators.values()) {
            if (accumulator.recordCount == EXPECTED_TRAIN_DAYS
                    && accumulator.dates.size() == EXPECTED_TRAIN_DAYS) {
                profiles.add(new SeriesProfile(accumulator.storeId, accumulator.itemId,
                        accumulator.salesSum / accumulator.recordCount));
            }
        }
        if (profiles.size() < 3) {
            throw new IllegalArgumentException("Expected at least three complete store-item series in train (2013-2015).");
        }
        profiles.sort(Comparator.comparingInt(SeriesProfile::getStoreId).thenComparingInt(SeriesProfile::getItemId));
        return profiles;
    }

    public static String formatReport(MultiSeriesResult result) {
        if (result == null) { throw new IllegalArgumentException("Multi-series result is required."); }
        StringBuilder report = new StringBuilder();
        report.append("MULTI-SERIES VALIDATION EXPERIMENT\n")
                .append("Seeds: ").append(result.seeds).append('\n')
                .append("Strata: tertiles by mean daily sales in train period 2013-01-01..2015-12-31\n")
                .append("Per-series Direct/AR metrics are averaged across seeds first. Macro values are then\n")
                .append("unweighted averages across selected series; ± is sample std across series means.\n")
                .append("Weekly naive is evaluated once per series. Validation only; test split is not evaluated.\n\n")
                .append(String.format(Locale.ROOT,
                        "%-7s %-11s %-10s %10s %10s %10s %10s %10s %10s %10s%n",
                        "Stratum", "Store", "Item", "TrainMean", "NaiveMAE", "NaiveRMSE",
                        "DirectMAE", "DirectRMSE", "AR_MAE", "AR_RMSE"));
        for (SeriesResult series : result.seriesResults) {
            report.append(String.format(Locale.ROOT, "%-7s %-11d %-10d %10.4f %10.4f %10.4f %10.4f %10.4f %10.4f %10.4f%n",
                    series.selection.stratum, series.getStoreId(), series.getItemId(),
                    series.selection.profile.trainMeanSales, series.weeklyMetrics.getMae(),
                    series.weeklyMetrics.getRmse(), series.getDirectMaeMean(), series.getDirectRmseMean(),
                    series.getAutoregressiveMaeMean(), series.getAutoregressiveRmseMean()));
        }
        report.append('\n').append(String.format(Locale.ROOT, "Macro across %d series (mean ± between-series sample std)%n",
                result.seriesResults.size()));
        appendMacro(report, "Weekly naive", summarize(result.seriesResults, Method.WEEKLY, Metric.MAE),
                summarize(result.seriesResults, Method.WEEKLY, Metric.RMSE));
        appendMacro(report, "Direct LSTM", summarize(result.seriesResults, Method.DIRECT, Metric.MAE),
                summarize(result.seriesResults, Method.DIRECT, Metric.RMSE));
        appendMacro(report, "Autoregressive LSTM", summarize(result.seriesResults, Method.AR, Metric.MAE),
                summarize(result.seriesResults, Method.AR, Metric.RMSE));
        return report.toString();
    }

    public static void writeReport(MultiSeriesResult result, Path outputPath) throws IOException {
        if (outputPath == null) { throw new IllegalArgumentException("Output path is required."); }
        Path parent = outputPath.toAbsolutePath().getParent();
        if (parent != null) { Files.createDirectories(parent); }
        Files.writeString(outputPath, formatReport(result), StandardCharsets.UTF_8);
    }

    private static void appendMacro(StringBuilder report, String name, MacroStats mae, MacroStats rmse) {
        report.append(String.format(Locale.ROOT, "%s: MAE %.4f ± %.4f | RMSE %.4f ± %.4f%n",
                name, mae.mean, mae.sampleStandardDeviation, rmse.mean, rmse.sampleStandardDeviation));
    }

    private static MacroStats summarize(List<SeriesResult> results, Method method, Metric metric) {
        List<Double> values = new ArrayList<>(results.size());
        for (SeriesResult result : results) {
            switch (method) {
                case WEEKLY:
                    values.add(metric == Metric.MAE ? result.weeklyMetrics.getMae() : result.weeklyMetrics.getRmse());
                    break;
                case DIRECT:
                    values.add(metric == Metric.MAE ? result.getDirectMaeMean() : result.getDirectRmseMean());
                    break;
                case AR:
                    values.add(metric == Metric.MAE ? result.getAutoregressiveMaeMean()
                            : result.getAutoregressiveRmseMean());
                    break;
                default: throw new IllegalStateException("Unsupported method: " + method);
            }
        }
        double mean = values.stream().mapToDouble(Double::doubleValue).average().orElseThrow();
        if (values.size() == 1) { return new MacroStats(mean, 0.0); }
        double sumSquares = 0.0;
        for (double value : values) { sumSquares += (value - mean) * (value - mean); }
        return new MacroStats(mean, Math.sqrt(sumSquares / (values.size() - 1)));
    }

    private enum Method { WEEKLY, DIRECT, AR }
    private enum Metric { MAE, RMSE }

    private static final class SeriesKey {
        private final int storeId;
        private final int itemId;
        private SeriesKey(int storeId, int itemId) { this.storeId = storeId; this.itemId = itemId; }
        @Override public boolean equals(Object other) {
            if (this == other) { return true; }
            if (!(other instanceof SeriesKey)) { return false; }
            SeriesKey that = (SeriesKey) other;
            return storeId == that.storeId && itemId == that.itemId;
        }
        @Override public int hashCode() { return 31 * storeId + itemId; }
    }

    private static final class ProfileAccumulator {
        private final int storeId;
        private final int itemId;
        private final Set<LocalDate> dates = new HashSet<>();
        private long recordCount;
        private double salesSum;
        private ProfileAccumulator(int storeId, int itemId) { this.storeId = storeId; this.itemId = itemId; }
        private void add(LocalDate date, int sales) { recordCount++; dates.add(date); salesSum += sales; }
    }

    public static final class SeriesProfile {
        private final int storeId;
        private final int itemId;
        private final double trainMeanSales;
        SeriesProfile(int storeId, int itemId, double trainMeanSales) {
            this.storeId = storeId;
            this.itemId = itemId;
            this.trainMeanSales = trainMeanSales;
        }
        public int getStoreId() { return storeId; }
        public int getItemId() { return itemId; }
        public double getTrainMeanSales() { return trainMeanSales; }
    }

    public static final class SelectedSeries {
        private final String stratum;
        private final SeriesProfile profile;
        private SelectedSeries(String stratum, SeriesProfile profile) { this.stratum = stratum; this.profile = profile; }
        public String getStratum() { return stratum; }
        public SeriesProfile getProfile() { return profile; }
    }

    public static final class MultiSeriesResult {
        private final List<Long> seeds;
        private final List<SeriesResult> seriesResults;
        private MultiSeriesResult(List<Long> seeds, List<SeriesResult> seriesResults) {
            this.seeds = List.copyOf(seeds);
            this.seriesResults = List.copyOf(seriesResults);
        }
        public List<Long> getSeeds() { return seeds; }
        public List<SeriesResult> getSeriesResults() { return seriesResults; }
    }

    public static final class SeriesResult {
        private final SelectedSeries selection;
        private final WeeklyNaiveBaseline.BaselineResult weeklyMetrics;
        private final List<ExperimentRunner.SeedResult> seedResults;
        private SeriesResult(SelectedSeries selection, WeeklyNaiveBaseline.BaselineResult weeklyMetrics,
                             List<ExperimentRunner.SeedResult> seedResults) {
            this.selection = selection;
            this.weeklyMetrics = weeklyMetrics;
            this.seedResults = List.copyOf(seedResults);
        }
        public String getStratum() { return selection.stratum; }
        public int getStoreId() { return selection.profile.storeId; }
        public int getItemId() { return selection.profile.itemId; }
        public double getTrainMeanSales() { return selection.profile.trainMeanSales; }
        public WeeklyNaiveBaseline.BaselineResult getWeeklyMetrics() { return weeklyMetrics; }
        public List<ExperimentRunner.SeedResult> getSeedResults() { return seedResults; }
        public double getDirectMaeMean() { return seedResults.stream().mapToDouble(ExperimentRunner.SeedResult::getDirectMae).average().orElseThrow(); }
        public double getDirectRmseMean() { return seedResults.stream().mapToDouble(ExperimentRunner.SeedResult::getDirectRmse).average().orElseThrow(); }
        public double getAutoregressiveMaeMean() { return seedResults.stream().mapToDouble(row -> row.getAutoregressiveMetrics().getMae()).average().orElseThrow(); }
        public double getAutoregressiveRmseMean() { return seedResults.stream().mapToDouble(row -> row.getAutoregressiveMetrics().getRmse()).average().orElseThrow(); }
    }

    private static final class MacroStats {
        private final double mean;
        private final double sampleStandardDeviation;
        private MacroStats(double mean, double sampleStandardDeviation) {
            this.mean = mean;
            this.sampleStandardDeviation = sampleStandardDeviation;
        }
    }
}
