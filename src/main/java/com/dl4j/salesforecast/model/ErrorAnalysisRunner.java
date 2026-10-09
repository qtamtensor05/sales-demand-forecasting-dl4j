package com.dl4j.salesforecast.model;

import com.dl4j.salesforecast.evaluation.WeeklyNaiveBaseline;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.function.Function;

/** Aggregates validation-only forecast errors without training or reading the test split. */
public final class ErrorAnalysisRunner {
    private ErrorAnalysisRunner() { }

    public static AnalysisResult analyze(MultiSeriesExperimentRunner.MultiSeriesResult result) {
        if (result == null) { throw new IllegalArgumentException("Multi-series result is required."); }
        List<SeriesInput> inputs = new ArrayList<>();
        for (MultiSeriesExperimentRunner.SeriesResult series : result.getSeriesResults()) {
            List<SeedInput> seeds = new ArrayList<>();
            for (ExperimentRunner.SeedResult seed : series.getSeedResults()) {
                seeds.add(new SeedInput(seed.getSeed(), fromDirect(seed.getDirectMetrics()),
                        fromAutoregressive(seed.getAutoregressiveMetrics())));
            }
            inputs.add(new SeriesInput(series.getStratum(), series.getStoreId(), series.getItemId(),
                    fromBaseline(series.getWeeklyMetrics()), seeds));
        }
        return analyze(inputs);
    }

    public static AnalysisResult analyze(List<SeriesInput> seriesInputs) {
        validateInputs(seriesInputs);
        List<SeriesAggregate> series = new ArrayList<>();
        for (SeriesInput input : seriesInputs) { series.add(aggregateSeries(input)); }

        MetricSummary overallBaseline = summarizeMapped(series, row -> row.baseline);
        MetricSummary overallDirect = summarizeMapped(series, row -> row.directMean);
        MetricSummary overallAr = summarizeMapped(series, row -> row.autoregressiveMean);

        Map<String, List<SeriesAggregate>> grouped = new LinkedHashMap<>();
        for (String stratum : List.of("LOW", "MEDIUM", "HIGH")) { grouped.put(stratum, new ArrayList<>()); }
        for (SeriesAggregate row : series) { grouped.computeIfAbsent(row.input.stratum, key -> new ArrayList<>()).add(row); }
        List<StratumSummary> strata = new ArrayList<>();
        for (Map.Entry<String, List<SeriesAggregate>> entry : grouped.entrySet()) {
            if (!entry.getValue().isEmpty()) {
                strata.add(new StratumSummary(entry.getKey(), entry.getValue().size(),
                        summarizeMapped(entry.getValue(), row -> row.baseline),
                        summarizeMapped(entry.getValue(), row -> row.directMean),
                        summarizeMapped(entry.getValue(), row -> row.autoregressiveMean)));
            }
        }

        int horizonCount = series.get(0).baseline.horizons.size();
        List<HorizonSummary> horizons = new ArrayList<>();
        for (int index = 0; index < horizonCount; index++) {
            final int h = index;
            horizons.add(new HorizonSummary(index + 1,
                    summarizeMapped(series, row -> row.baseline.horizons.get(h).asMetrics()),
                    summarizeMapped(series, row -> row.directMean.horizons.get(h).asMetrics()),
                    summarizeMapped(series, row -> row.autoregressiveMean.horizons.get(h).asMetrics())));
        }

        List<SeedSummary> seeds = new ArrayList<>();
        for (int index = 0; index < seriesInputs.get(0).seeds.size(); index++) {
            final int seedIndex = index;
            long seed = seriesInputs.get(0).seeds.get(index).seed;
            seeds.add(new SeedSummary(seed,
                    summarizeMapped(seriesInputs, row -> row.seeds.get(seedIndex).direct),
                    summarizeMapped(seriesInputs, row -> row.seeds.get(seedIndex).autoregressive)));
        }

        int directWins = 0;
        int arWins = 0;
        for (SeriesAggregate row : series) {
            if (row.directMean.rmse < row.baseline.rmse) { directWins++; }
            if (row.autoregressiveMean.rmse < row.baseline.rmse) { arWins++; }
        }
        return new AnalysisResult(series.size(), seriesInputs.get(0).seeds.size(), horizonCount,
                overallBaseline, overallDirect, overallAr, strata, horizons, seeds, directWins, arWins);
    }

    public static MetricSummary summarize(List<MethodMetrics> values) {
        if (values == null || values.isEmpty()) {
            throw new IllegalArgumentException("At least one metric sample is required.");
        }
        List<Double> maes = new ArrayList<>();
        List<Double> rmses = new ArrayList<>();
        long forecastCount = 0;
        for (MethodMetrics value : values) {
            if (value == null) { throw new IllegalArgumentException("Metric samples must not be null."); }
            maes.add(value.mae);
            rmses.add(value.rmse);
            forecastCount += value.forecastCount;
        }
        ExperimentRunner.Stats mae = ExperimentRunner.summarizeValues(maes);
        ExperimentRunner.Stats rmse = ExperimentRunner.summarizeValues(rmses);
        return new MetricSummary(values.size(), forecastCount, mae.getMean(), mae.getStandardDeviation(),
                rmse.getMean(), rmse.getStandardDeviation());
    }

    public static double improvementPercent(double baselineError, double modelError) {
        if (!(baselineError > 0.0) || !Double.isFinite(baselineError) || !Double.isFinite(modelError)) {
            throw new IllegalArgumentException("Improvement needs a positive finite baseline and finite model error.");
        }
        return (baselineError - modelError) * 100.0 / baselineError;
    }

    public static String formatReport(AnalysisResult result) {
        if (result == null) { throw new IllegalArgumentException("Analysis result is required."); }
        StringBuilder out = new StringBuilder();
        out.append("VALIDATION ERROR ANALYSIS\n")
                .append("Validation only; the 2017 test split is not read or reported.\n")
                .append(String.format(Locale.ROOT, "Series: %d | Seeds per neural model: %d | Horizons: %d%n",
                        result.seriesCount, result.seedCount, result.horizonCount))
                .append("Macro aggregation: average each neural metric across seeds within a series first, then\n")
                .append("average series with equal weight. ± is sample standard deviation across series means.\n")
                .append("Forecast counts refer to rolling-origin predictions; target dates overlap across windows.\n")
                .append("Improvement % = (weekly naive error - model error) / weekly naive error * 100; positive is better.\n\n")
                .append("OVERALL MACRO\n");
        appendComparison(out, "All series", result.overallBaseline, result.overallDirect, result.overallAr);

        out.append("\nBY SALES STRATUM\n");
        for (StratumSummary row : result.strata) {
            appendComparison(out, row.stratum + " (series=" + row.seriesCount + ")",
                    row.baseline, row.direct, row.autoregressive);
        }

        out.append("\nBY FORECAST HORIZON\n");
        for (HorizonSummary row : result.horizons) {
            appendComparison(out, "Horizon " + row.horizonDays, row.baseline, row.direct, row.autoregressive);
        }

        out.append("\nBY SEED (macro across series; weekly naive is seed-independent)\n");
        for (SeedSummary row : result.seeds) {
            out.append(String.format(Locale.ROOT,
                    "Seed %d | Direct MAE %.4f ± %.4f RMSE %.4f ± %.4f | AR MAE %.4f ± %.4f RMSE %.4f ± %.4f%n",
                    row.seed, row.direct.maeMean, row.direct.maeSampleStandardDeviation,
                    row.direct.rmseMean, row.direct.rmseSampleStandardDeviation,
                    row.autoregressive.maeMean, row.autoregressive.maeSampleStandardDeviation,
                    row.autoregressive.rmseMean, row.autoregressive.rmseSampleStandardDeviation));
        }
        out.append(String.format(Locale.ROOT,
                "%nRMSE wins vs weekly naive (per-series neural seed mean): Direct %d/%d | Autoregressive %d/%d%n",
                result.directWinsByRmse, result.seriesCount, result.autoregressiveWinsByRmse, result.seriesCount));
        return out.toString();
    }

    public static void writeReport(AnalysisResult result, Path outputPath) throws IOException {
        if (outputPath == null) { throw new IllegalArgumentException("Output path is required."); }
        Path parent = outputPath.toAbsolutePath().getParent();
        if (parent != null) { Files.createDirectories(parent); }
        Files.writeString(outputPath, formatReport(result), StandardCharsets.UTF_8);
    }

    private static void appendComparison(StringBuilder out, String label, MetricSummary baseline,
                                         MetricSummary direct, MetricSummary ar) {
        out.append(label).append('\n');
        out.append(String.format(Locale.ROOT, "  Macro samples: %d series | rolling forecasts: %d%n",
                baseline.sampleCount, baseline.forecastCount));
        out.append(String.format(Locale.ROOT, "  Weekly naive | MAE %.4f ± %.4f | RMSE %.4f ± %.4f%n",
                baseline.maeMean, baseline.maeSampleStandardDeviation,
                baseline.rmseMean, baseline.rmseSampleStandardDeviation));
        appendModel(out, "Direct LSTM", baseline, direct);
        appendModel(out, "AR LSTM", baseline, ar);
    }

    private static void appendModel(StringBuilder out, String name, MetricSummary baseline, MetricSummary model) {
        out.append(String.format(Locale.ROOT,
                "  %-12s | MAE %.4f ± %.4f (%+.2f%%) | RMSE %.4f ± %.4f (%+.2f%%)%n",
                name, model.maeMean, model.maeSampleStandardDeviation,
                improvementPercent(baseline.maeMean, model.maeMean),
                model.rmseMean, model.rmseSampleStandardDeviation,
                improvementPercent(baseline.rmseMean, model.rmseMean)));
    }

    private static <T> MetricSummary summarizeMapped(List<T> rows, Function<T, MethodMetrics> mapper) {
        List<MethodMetrics> values = new ArrayList<>();
        for (T row : rows) { values.add(mapper.apply(row)); }
        return summarize(values);
    }

    private static SeriesAggregate aggregateSeries(SeriesInput input) {
        MethodMetrics direct = meanMetrics(input.seeds, seed -> seed.direct);
        MethodMetrics ar = meanMetrics(input.seeds, seed -> seed.autoregressive);
        return new SeriesAggregate(input, input.baseline, direct, ar);
    }

    private static MethodMetrics meanMetrics(List<SeedInput> seeds, Function<SeedInput, MethodMetrics> mapper) {
        long count = mapper.apply(seeds.get(0)).forecastCount;
        int horizons = mapper.apply(seeds.get(0)).horizons.size();
        double mae = 0.0;
        double rmse = 0.0;
        List<HorizonMetric> horizonMeans = new ArrayList<>();
        for (SeedInput seed : seeds) {
            MethodMetrics metrics = mapper.apply(seed);
            mae += metrics.mae;
            rmse += metrics.rmse;
        }
        for (int h = 0; h < horizons; h++) {
            double horizonMae = 0.0;
            double horizonRmse = 0.0;
            for (SeedInput seed : seeds) {
                HorizonMetric metric = mapper.apply(seed).horizons.get(h);
                horizonMae += metric.mae;
                horizonRmse += metric.rmse;
            }
            HorizonMetric first = mapper.apply(seeds.get(0)).horizons.get(h);
            horizonMeans.add(new HorizonMetric(first.horizonDays, first.forecastCount,
                    horizonMae / seeds.size(), horizonRmse / seeds.size()));
        }
        return new MethodMetrics(count, mae / seeds.size(), rmse / seeds.size(), horizonMeans);
    }

    private static void validateInputs(List<SeriesInput> inputs) {
        if (inputs == null || inputs.isEmpty()) { throw new IllegalArgumentException("At least one series is required."); }
        List<Long> expectedSeeds = seedIds(inputs.get(0));
        int expectedHorizons = inputs.get(0).baseline.horizons.size();
        if (expectedSeeds.isEmpty() || expectedHorizons == 0) {
            throw new IllegalArgumentException("Each series needs seeds and horizon metrics.");
        }
        for (SeriesInput input : inputs) {
            if (input == null || input.stratum == null || input.stratum.isBlank()
                    || !seedIds(input).equals(expectedSeeds) || input.baseline.horizons.size() != expectedHorizons) {
                throw new IllegalArgumentException("Series must have aligned strata, seeds and horizons.");
            }
            validateMetrics(input.baseline, expectedHorizons);
            for (SeedInput seed : input.seeds) {
                validateMetrics(seed.direct, expectedHorizons);
                validateMetrics(seed.autoregressive, expectedHorizons);
            }
        }
    }

    private static List<Long> seedIds(SeriesInput input) {
        List<Long> ids = new ArrayList<>();
        for (SeedInput seed : input.seeds) { ids.add(seed.seed); }
        return ids;
    }

    private static void validateMetrics(MethodMetrics metrics, int horizons) {
        if (metrics == null || metrics.forecastCount <= 0 || metrics.horizons.size() != horizons
                || !Double.isFinite(metrics.mae) || metrics.mae < 0
                || !Double.isFinite(metrics.rmse) || metrics.rmse < 0) {
            throw new IllegalArgumentException("Metrics must be finite, non-negative and horizon-aligned.");
        }
        for (int h = 0; h < horizons; h++) {
            HorizonMetric metric = metrics.horizons.get(h);
            if (metric.horizonDays != h + 1 || metric.forecastCount <= 0
                    || !Double.isFinite(metric.mae) || metric.mae < 0
                    || !Double.isFinite(metric.rmse) || metric.rmse < 0) {
                throw new IllegalArgumentException("Horizon metrics must be ordered, finite and non-negative.");
            }
        }
    }

    private static MethodMetrics fromBaseline(WeeklyNaiveBaseline.BaselineResult metrics) {
        List<HorizonMetric> horizons = new ArrayList<>();
        for (WeeklyNaiveBaseline.HorizonMetrics row : metrics.getHorizons()) {
            horizons.add(new HorizonMetric(row.getHorizonDays(), row.getForecastCount(), row.getMae(), row.getRmse()));
        }
        return new MethodMetrics(metrics.getForecastCount(), metrics.getMae(), metrics.getRmse(), horizons);
    }

    private static MethodMetrics fromDirect(SalesLstmForecaster.ForecastMetrics metrics) {
        List<HorizonMetric> horizons = new ArrayList<>();
        for (SalesLstmForecaster.HorizonMetrics row : metrics.getHorizons()) {
            horizons.add(new HorizonMetric(row.getHorizonDays(), row.getForecastCount(), row.getMae(), row.getRmse()));
        }
        return new MethodMetrics(metrics.getForecastCount(), metrics.getMae(), metrics.getRmse(), horizons);
    }

    private static MethodMetrics fromAutoregressive(AutoregressiveLstmForecaster.ForecastMetrics metrics) {
        List<HorizonMetric> horizons = new ArrayList<>();
        for (AutoregressiveLstmForecaster.HorizonMetrics row : metrics.getHorizons()) {
            horizons.add(new HorizonMetric(row.getHorizonDays(), row.getForecastCount(), row.getMae(), row.getRmse()));
        }
        return new MethodMetrics(metrics.getForecastCount(), metrics.getMae(), metrics.getRmse(), horizons);
    }

    public static final class HorizonMetric {
        private final int horizonDays;
        private final long forecastCount;
        private final double mae;
        private final double rmse;
        public HorizonMetric(int horizonDays, long forecastCount, double mae, double rmse) {
            this.horizonDays = horizonDays; this.forecastCount = forecastCount; this.mae = mae; this.rmse = rmse;
        }
        private MethodMetrics asMetrics() { return new MethodMetrics(forecastCount, mae, rmse, List.of(this)); }
    }

    public static final class MethodMetrics {
        private final long forecastCount;
        private final double mae;
        private final double rmse;
        private final List<HorizonMetric> horizons;
        public MethodMetrics(long forecastCount, double mae, double rmse, List<HorizonMetric> horizons) {
            this.forecastCount = forecastCount; this.mae = mae; this.rmse = rmse; this.horizons = List.copyOf(horizons);
        }
    }

    public static final class SeedInput {
        private final long seed;
        private final MethodMetrics direct;
        private final MethodMetrics autoregressive;
        public SeedInput(long seed, MethodMetrics direct, MethodMetrics autoregressive) {
            this.seed = seed; this.direct = direct; this.autoregressive = autoregressive;
        }
    }

    public static final class SeriesInput {
        private final String stratum;
        private final int storeId;
        private final int itemId;
        private final MethodMetrics baseline;
        private final List<SeedInput> seeds;
        public SeriesInput(String stratum, int storeId, int itemId, MethodMetrics baseline, List<SeedInput> seeds) {
            this.stratum = stratum; this.storeId = storeId; this.itemId = itemId;
            this.baseline = baseline; this.seeds = List.copyOf(seeds);
        }
    }

    public static final class MetricSummary {
        private final int sampleCount;
        private final long forecastCount;
        private final double maeMean;
        private final double maeSampleStandardDeviation;
        private final double rmseMean;
        private final double rmseSampleStandardDeviation;
        private MetricSummary(int sampleCount, long forecastCount, double maeMean, double maeStd,
                              double rmseMean, double rmseStd) {
            this.sampleCount = sampleCount; this.forecastCount = forecastCount;
            this.maeMean = maeMean; this.maeSampleStandardDeviation = maeStd;
            this.rmseMean = rmseMean; this.rmseSampleStandardDeviation = rmseStd;
        }
        public int getSampleCount() { return sampleCount; }
        public long getForecastCount() { return forecastCount; }
        public double getMaeMean() { return maeMean; }
        public double getMaeSampleStandardDeviation() { return maeSampleStandardDeviation; }
        public double getRmseMean() { return rmseMean; }
        public double getRmseSampleStandardDeviation() { return rmseSampleStandardDeviation; }
    }

    public static final class StratumSummary {
        private final String stratum; private final int seriesCount;
        private final MetricSummary baseline; private final MetricSummary direct; private final MetricSummary autoregressive;
        private StratumSummary(String stratum, int count, MetricSummary baseline, MetricSummary direct, MetricSummary ar) {
            this.stratum = stratum; this.seriesCount = count; this.baseline = baseline; this.direct = direct; this.autoregressive = ar;
        }
    }

    public static final class HorizonSummary {
        private final int horizonDays;
        private final MetricSummary baseline; private final MetricSummary direct; private final MetricSummary autoregressive;
        private HorizonSummary(int horizonDays, MetricSummary baseline, MetricSummary direct, MetricSummary ar) {
            this.horizonDays = horizonDays; this.baseline = baseline; this.direct = direct; this.autoregressive = ar;
        }
    }

    public static final class SeedSummary {
        private final long seed; private final MetricSummary direct; private final MetricSummary autoregressive;
        private SeedSummary(long seed, MetricSummary direct, MetricSummary ar) {
            this.seed = seed; this.direct = direct; this.autoregressive = ar;
        }
    }

    public static final class AnalysisResult {
        private final int seriesCount; private final int seedCount; private final int horizonCount;
        private final MetricSummary overallBaseline; private final MetricSummary overallDirect; private final MetricSummary overallAr;
        private final List<StratumSummary> strata; private final List<HorizonSummary> horizons; private final List<SeedSummary> seeds;
        private final int directWinsByRmse; private final int autoregressiveWinsByRmse;
        private AnalysisResult(int seriesCount, int seedCount, int horizonCount, MetricSummary baseline,
                               MetricSummary direct, MetricSummary ar, List<StratumSummary> strata,
                               List<HorizonSummary> horizons, List<SeedSummary> seeds, int directWins, int arWins) {
            this.seriesCount = seriesCount; this.seedCount = seedCount; this.horizonCount = horizonCount;
            this.overallBaseline = baseline; this.overallDirect = direct; this.overallAr = ar;
            this.strata = List.copyOf(strata); this.horizons = List.copyOf(horizons); this.seeds = List.copyOf(seeds);
            this.directWinsByRmse = directWins; this.autoregressiveWinsByRmse = arWins;
        }
        public int getSeriesCount() { return seriesCount; }
        public int getSeedCount() { return seedCount; }
        public List<StratumSummary> getStrata() { return strata; }
        public List<HorizonSummary> getHorizons() { return horizons; }
        public List<SeedSummary> getSeeds() { return seeds; }
        public int getDirectWinsByRmse() { return directWinsByRmse; }
        public int getAutoregressiveWinsByRmse() { return autoregressiveWinsByRmse; }
        public double getOverallDirectRmseImprovementPercent() {
            return improvementPercent(overallBaseline.rmseMean, overallDirect.rmseMean);
        }
        public double getOverallAutoregressiveRmseImprovementPercent() {
            return improvementPercent(overallBaseline.rmseMean, overallAr.rmseMean);
        }
    }

    private static final class SeriesAggregate {
        private final SeriesInput input; private final MethodMetrics baseline;
        private final MethodMetrics directMean; private final MethodMetrics autoregressiveMean;
        private SeriesAggregate(SeriesInput input, MethodMetrics baseline, MethodMetrics direct, MethodMetrics ar) {
            this.input = input; this.baseline = baseline; this.directMean = direct; this.autoregressiveMean = ar;
        }
    }
}
