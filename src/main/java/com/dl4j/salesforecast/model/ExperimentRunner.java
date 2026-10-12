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
        return run(prepared, seeds, -1, -1, null, -1, -1);
    }

    static ExperimentResult run(PreprocessingResult prepared, List<Long> seeds,
                                int seriesIndex, int totalSeries, String stratum,
                                int storeId, int itemId) {
        return run(prepared, seeds, seriesIndex, totalSeries, stratum, storeId, itemId, ExperimentConfiguration.legacy());
    }

    static ExperimentResult run(PreprocessingResult prepared, List<Long> seeds,
                                int seriesIndex, int totalSeries, String stratum,
                                int storeId, int itemId, ExperimentConfiguration configuration) {
        if (prepared == null) { throw new IllegalArgumentException("Preprocessing result is required."); }
        if (seeds == null || seeds.isEmpty()) { throw new IllegalArgumentException("At least one seed is required."); }
        if (seeds.stream().anyMatch(seed -> seed == null) || seeds.stream().distinct().count() != seeds.size()) {
            throw new IllegalArgumentException("Seeds must be non-null and unique.");
        }

        WeeklyNaiveBaseline.BaselineResult weekly = WeeklyNaiveBaseline.evaluate(prepared.getValidation());
        List<SeedResult> results = new ArrayList<>(seeds.size());
        int epochs = configuration.getMaxEpochs();
        int patience = configuration.getPatience();
        for (int seedIndex = 0; seedIndex < seeds.size(); seedIndex++) {
            long seed = seeds.get(seedIndex);
            System.out.printf("%n=== Validation experiment: seed %d ===%n", seed);
            if (configuration.isHuberComparison()) {
                List<AutoregressiveLstmForecaster.FitResult> fits = new ArrayList<>();
                for (LossMode loss : LossMode.values()) {
                    TrainingProgress progress = seriesIndex < 0 ? null : TrainingProgress.forFit(
                            seriesIndex, totalSeries, seedIndex, seeds.size(), loss.ordinal(), configuration.getModels(),
                            stratum, storeId, itemId, seed, epochs);
                    fits.add(ResidualLstmForecaster.fit(prepared, epochs, DEFAULT_HIDDEN_UNITS,
                            DEFAULT_LEARNING_RATE, patience, seed, progress, loss));
                }
                results.add(new SeedResult(seed, fits.get(0), fits.get(1)));
                continue;
            }
            TrainingProgress directProgress = seriesIndex < 0 ? null : TrainingProgress.forFit(
                    seriesIndex, totalSeries, seedIndex, seeds.size(), 0, configuration.getModels(),
                    stratum, storeId, itemId, seed, epochs);
            TrainingProgress autoregressiveProgress = seriesIndex < 0 ? null : TrainingProgress.forFit(
                    seriesIndex, totalSeries, seedIndex, seeds.size(), 1, configuration.getModels(),
                    stratum, storeId, itemId, seed, epochs);
            SalesLstmForecaster.FitResult direct = SalesLstmForecaster.fit(prepared, epochs,
                    DEFAULT_HIDDEN_UNITS, DEFAULT_LEARNING_RATE, patience, seed, directProgress);
            AutoregressiveLstmForecaster.FitResult autoregressive = AutoregressiveLstmForecaster.fit(prepared,
                    epochs, DEFAULT_HIDDEN_UNITS, DEFAULT_LEARNING_RATE, patience, seed,
                    autoregressiveProgress);
            AutoregressiveLstmForecaster.FitResult residual = configuration.includesResidual()
                    ? ResidualLstmForecaster.fit(prepared, epochs, DEFAULT_HIDDEN_UNITS, DEFAULT_LEARNING_RATE,
                            patience, seed, seriesIndex < 0 ? null : TrainingProgress.forFit(
                                    seriesIndex, totalSeries, seedIndex, seeds.size(), 2, configuration.getModels(),
                                    stratum, storeId, itemId, seed, epochs)) : null;
            results.add(new SeedResult(seed, direct.getValidationMetrics(), autoregressive.getValidationMetrics(),
                    direct.getBestEpoch(), autoregressive.getBestEpoch(),
                    direct.getEpochsRun(), autoregressive.getEpochsRun(),
                    direct.getHistory(), autoregressive.getHistory(), residual));
            SeedResult row = results.get(results.size() - 1);
            System.out.printf(Locale.ROOT,
                    "Seed %d | Direct MAE %.4f RMSE %.4f (epoch %d/%d) | AR MAE %.4f RMSE %.4f (epoch %d/%d)%n",
                    seed, row.getDirectMae(), row.getDirectRmse(),
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
                values.add(mae ? result.getDirectMae() : result.getDirectRmse());
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
        private final ResidualResult residual;
        private final ResidualResult residualHuber;
        private final SalesLstmForecaster.ForecastMetrics directMetrics;
        private final AutoregressiveLstmForecaster.ForecastMetrics autoregressiveMetrics;
        private final int directBestEpoch;
        private final int autoregressiveBestEpoch;
        private final int directEpochsRun;
        private final int autoregressiveEpochsRun;
        private final List<SalesLstmForecaster.EpochMetrics> directHistory;
        private final List<AutoregressiveLstmForecaster.EpochMetrics> autoregressiveHistory;
        private SeedResult(long seed, SalesLstmForecaster.ForecastMetrics directMetrics,
                           AutoregressiveLstmForecaster.ForecastMetrics autoregressiveMetrics,
                           int directBestEpoch, int autoregressiveBestEpoch,
                           int directEpochsRun, int autoregressiveEpochsRun,
                           List<SalesLstmForecaster.EpochMetrics> directHistory,
                           List<AutoregressiveLstmForecaster.EpochMetrics> autoregressiveHistory,
                           AutoregressiveLstmForecaster.FitResult residual) {
            this.seed = seed;
            this.residual = residual == null ? null : new ResidualResult(residual);
            this.residualHuber = null;
            this.directMetrics = directMetrics;
            this.autoregressiveMetrics = autoregressiveMetrics;
            this.directBestEpoch = directBestEpoch;
            this.autoregressiveBestEpoch = autoregressiveBestEpoch;
            this.directEpochsRun = directEpochsRun;
            this.autoregressiveEpochsRun = autoregressiveEpochsRun;
            this.directHistory = List.copyOf(directHistory);
            this.autoregressiveHistory = List.copyOf(autoregressiveHistory);
        }
        public long getSeed() { return seed; }
        private SeedResult(long seed, AutoregressiveLstmForecaster.FitResult mse,
                           AutoregressiveLstmForecaster.FitResult huber) {
            this.seed = seed;
            this.residual = new ResidualResult(mse);
            this.residualHuber = new ResidualResult(huber);
            this.directMetrics = null; this.autoregressiveMetrics = null;
            this.directBestEpoch = 0; this.autoregressiveBestEpoch = 0;
            this.directEpochsRun = 0; this.autoregressiveEpochsRun = 0;
            this.directHistory = List.of(); this.autoregressiveHistory = List.of();
        }
        public ResidualResult getResidualHuber() { return residualHuber; }
        public ResidualResult getResidual() { return residual; }
        public double getDirectMae() { return directMetrics.getMae(); }
        public double getDirectRmse() { return directMetrics.getRmse(); }
        public SalesLstmForecaster.ForecastMetrics getDirectMetrics() { return directMetrics; }
        public AutoregressiveLstmForecaster.ForecastMetrics getAutoregressiveMetrics() { return autoregressiveMetrics; }
        public int getDirectBestEpoch() { return directBestEpoch; }
        public int getAutoregressiveBestEpoch() { return autoregressiveBestEpoch; }
        public int getDirectEpochsRun() { return directEpochsRun; }
        public int getAutoregressiveEpochsRun() { return autoregressiveEpochsRun; }
        public List<SalesLstmForecaster.EpochMetrics> getDirectHistory() { return directHistory; }
        public List<AutoregressiveLstmForecaster.EpochMetrics> getAutoregressiveHistory() {
            return autoregressiveHistory;
        }
    }

    /** Retains evaluation evidence without keeping every trained network resident. */
    public static final class ResidualResult {
        private final AutoregressiveLstmForecaster.ForecastMetrics metrics;
        private final List<AutoregressiveLstmForecaster.EpochMetrics> history;
        private final int bestEpoch;
        private final int epochsRun;
        private ResidualResult(AutoregressiveLstmForecaster.FitResult fit) {
            metrics = fit.getValidationMetrics(); history = fit.getHistory();
            bestEpoch = fit.getBestEpoch(); epochsRun = fit.getEpochsRun();
        }
        public AutoregressiveLstmForecaster.ForecastMetrics getValidationMetrics() { return metrics; }
        public List<AutoregressiveLstmForecaster.EpochMetrics> getHistory() { return history; }
        public int getBestEpoch() { return bestEpoch; }
        public int getEpochsRun() { return epochsRun; }
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
