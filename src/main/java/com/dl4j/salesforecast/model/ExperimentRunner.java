package com.dl4j.salesforecast.model;

import com.dl4j.salesforecast.evaluation.WeeklyNaiveBaseline;
import com.dl4j.salesforecast.preprocessing.SalesPreprocessor.PreprocessingResult;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/** Repeats validation-only model comparisons with fixed configurations and multiple seeds. */
public final class ExperimentRunner {
    public static final List<Long> DEFAULT_SEEDS = List.of(42L, 123L, 2026L, 7L, 99L);
    public static final int DEFAULT_HIDDEN_UNITS = 32;
    public static final double DEFAULT_LEARNING_RATE = 0.001;

    private ExperimentRunner() { }

    /** Runs direct and autoregressive models for every seed; it does not evaluate the test split. */
    public static ExperimentResult run(PreprocessingResult prepared) {
        return run(prepared, DEFAULT_SEEDS);
    }

    /** Exposed for reproducible experiments and fast synthetic smoke tests. */
    public static ExperimentResult run(PreprocessingResult prepared, List<Long> seeds) {
        if (prepared == null) { throw new IllegalArgumentException("Preprocessing result is required."); }
        if (seeds == null || seeds.isEmpty()) { throw new IllegalArgumentException("At least one seed is required."); }
        if (seeds.stream().anyMatch(seed -> seed == null) || seeds.stream().distinct().count() != seeds.size()) {
            throw new IllegalArgumentException("Seeds must be non-null and unique.");
        }

        WeeklyNaiveBaseline.BaselineResult weekly = WeeklyNaiveBaseline.evaluate(prepared.getValidation());
        List<SeedResult> results = new ArrayList<>(seeds.size());
        int epochs = SalesLstmForecaster.DEFAULT_SEARCH_EPOCHS;
        int patience = SalesLstmForecaster.DEFAULT_EARLY_STOPPING_PATIENCE;
        for (long seed : seeds) {
            System.out.printf("%n=== Validation experiment: seed %d ===%n", seed);
            SalesLstmForecaster.FitResult direct = SalesLstmForecaster.fit(prepared, epochs,
                    DEFAULT_HIDDEN_UNITS, DEFAULT_LEARNING_RATE, patience, seed);
            AutoregressiveLstmForecaster.FitResult autoregressive = AutoregressiveLstmForecaster.fit(prepared,
                    epochs, DEFAULT_HIDDEN_UNITS, DEFAULT_LEARNING_RATE, patience, seed);
            results.add(new SeedResult(seed, direct.getValidationMetrics().getMae(),
                    direct.getValidationMetrics().getRmse(), autoregressive.getValidationMetrics(),
                    direct.getBestEpoch(), autoregressive.getBestEpoch(),
                    direct.getEpochsRun(), autoregressive.getEpochsRun()));
            SeedResult row = results.get(results.size() - 1);
            System.out.printf(Locale.ROOT,
                    "Seed %d | Direct MAE %.4f RMSE %.4f (epoch %d/%d) | AR MAE %.4f RMSE %.4f (epoch %d/%d)%n",
                    seed, row.directMae, row.directRmse,
                    row.directBestEpoch, row.directEpochsRun, row.autoregressiveMetrics.getMae(),
                    row.autoregressiveMetrics.getRmse(), row.autoregressiveBestEpoch, row.autoregressiveEpochsRun);
        }
        return new ExperimentResult(seeds, weekly, results);
    }

    public static void printSummary(ExperimentResult result) {
        if (result == null) { throw new IllegalArgumentException("Experiment result is required."); }
        Stats directMae = summarize(result.seedResults, true, true);
        Stats directRmse = summarize(result.seedResults, true, false);
        Stats autoregressiveMae = summarize(result.seedResults, false, true);
        Stats autoregressiveRmse = summarize(result.seedResults, false, false);
        System.out.println("\n==========================================\nMULTI-SEED VALIDATION SUMMARY\n==========================================");
        System.out.println("Seeds: " + result.seeds);
        System.out.println("Standard deviation: sample std (n - 1); metrics in raw sales units.");
        System.out.printf(Locale.ROOT, "DIRECT LSTM%nMAE  : %.4f ± %.4f%nRMSE : %.4f ± %.4f%n",
                directMae.mean, directMae.standardDeviation, directRmse.mean, directRmse.standardDeviation);
        System.out.printf(Locale.ROOT,
                "AUTOREGRESSIVE LSTM%nMAE  : %.4f ± %.4f%nRMSE : %.4f ± %.4f%n",
                autoregressiveMae.mean, autoregressiveMae.standardDeviation,
                autoregressiveRmse.mean, autoregressiveRmse.standardDeviation);
        System.out.printf(Locale.ROOT, "WEEKLY NAIVE%nMAE  : %.4f%nRMSE : %.4f%n",
                result.weeklyMetrics.getMae(), result.weeklyMetrics.getRmse());
        String neuralWinner = directRmse.mean <= autoregressiveRmse.mean ? "DIRECT LSTM" : "AUTOREGRESSIVE LSTM";
        double bestNeuralMeanRmse = Math.min(directRmse.mean, autoregressiveRmse.mean);
        System.out.println("Lower mean neural RMSE: " + neuralWinner);
        System.out.println(bestNeuralMeanRmse < result.weeklyMetrics.getRmse()
                ? "Mean neural RMSE is below weekly naive."
                : "Weekly naive remains better by mean RMSE.");
        System.out.println("Validation only; no test metrics were computed.");
        System.out.println("==========================================");
    }

    private static Stats summarize(List<SeedResult> results, boolean direct, boolean mae) {
        List<Double> values = new ArrayList<>(results.size());
        for (SeedResult result : results) {
            if (direct) {
                values.add(mae ? result.directMae : result.directRmse);
            } else {
                values.add(mae ? result.autoregressiveMetrics.getMae() : result.autoregressiveMetrics.getRmse());
            }
        }
        return summarizeValues(values);
    }

    /** Mean and sample standard deviation; a single observation has zero observed spread. */
    public static Stats summarizeValues(List<Double> values) {
        if (values == null || values.isEmpty() || values.stream().anyMatch(value ->
                value == null || !Double.isFinite(value))) {
            throw new IllegalArgumentException("Statistics need at least one finite value.");
        }
        double mean = values.stream().mapToDouble(Double::doubleValue).average().orElseThrow();
        if (values.size() == 1) { return new Stats(mean, 0.0); }
        double squaredDifferences = 0.0;
        for (double value : values) {
            double difference = value - mean;
            squaredDifferences += difference * difference;
        }
        return new Stats(mean, Math.sqrt(squaredDifferences / (values.size() - 1)));
    }

    public static final class ExperimentResult {
        private final List<Long> seeds;
        private final WeeklyNaiveBaseline.BaselineResult weeklyMetrics;
        private final List<SeedResult> seedResults;
        private ExperimentResult(List<Long> seeds, WeeklyNaiveBaseline.BaselineResult weeklyMetrics,
                                 List<SeedResult> seedResults) {
            this.seeds = List.copyOf(seeds);
            this.weeklyMetrics = weeklyMetrics;
            this.seedResults = List.copyOf(seedResults);
        }
        public List<Long> getSeeds() { return seeds; }
        public WeeklyNaiveBaseline.BaselineResult getWeeklyMetrics() { return weeklyMetrics; }
        public List<SeedResult> getSeedResults() { return seedResults; }
        public Stats getDirectMaeStats() { return summarize(seedResults, true, true); }
        public Stats getDirectRmseStats() { return summarize(seedResults, true, false); }
        public Stats getAutoregressiveMaeStats() { return summarize(seedResults, false, true); }
        public Stats getAutoregressiveRmseStats() { return summarize(seedResults, false, false); }
    }

    public static final class SeedResult {
        private final long seed;
        private final double directMae;
        private final double directRmse;
        private final AutoregressiveLstmForecaster.ForecastMetrics autoregressiveMetrics;
        private final int directBestEpoch;
        private final int autoregressiveBestEpoch;
        private final int directEpochsRun;
        private final int autoregressiveEpochsRun;
        private SeedResult(long seed, double directMae, double directRmse,
                           AutoregressiveLstmForecaster.ForecastMetrics autoregressiveMetrics,
                           int directBestEpoch, int autoregressiveBestEpoch,
                           int directEpochsRun, int autoregressiveEpochsRun) {
            this.seed = seed;
            this.directMae = directMae;
            this.directRmse = directRmse;
            this.autoregressiveMetrics = autoregressiveMetrics;
            this.directBestEpoch = directBestEpoch;
            this.autoregressiveBestEpoch = autoregressiveBestEpoch;
            this.directEpochsRun = directEpochsRun;
            this.autoregressiveEpochsRun = autoregressiveEpochsRun;
        }
        public long getSeed() { return seed; }
        public double getDirectMae() { return directMae; }
        public double getDirectRmse() { return directRmse; }
        public AutoregressiveLstmForecaster.ForecastMetrics getAutoregressiveMetrics() { return autoregressiveMetrics; }
        public int getDirectBestEpoch() { return directBestEpoch; }
        public int getAutoregressiveBestEpoch() { return autoregressiveBestEpoch; }
        public int getDirectEpochsRun() { return directEpochsRun; }
        public int getAutoregressiveEpochsRun() { return autoregressiveEpochsRun; }
    }

    public static final class Stats {
        private final double mean;
        private final double standardDeviation;
        private Stats(double mean, double standardDeviation) {
            this.mean = mean;
            this.standardDeviation = standardDeviation;
        }
        public double getMean() { return mean; }
        public double getStandardDeviation() { return standardDeviation; }
    }
}
