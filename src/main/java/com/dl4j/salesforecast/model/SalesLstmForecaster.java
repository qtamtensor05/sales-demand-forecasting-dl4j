package com.dl4j.salesforecast.model;

import com.dl4j.salesforecast.preprocessing.SalesPreprocessor;
import com.dl4j.salesforecast.preprocessing.SalesPreprocessor.MinMaxScaler;
import com.dl4j.salesforecast.preprocessing.SalesPreprocessor.PreprocessingResult;
import com.dl4j.salesforecast.preprocessing.SalesPreprocessor.Split;
import com.dl4j.salesforecast.preprocessing.SalesPreprocessor.Window;
import org.deeplearning4j.nn.conf.MultiLayerConfiguration;
import org.deeplearning4j.nn.conf.NeuralNetConfiguration;
import org.deeplearning4j.nn.conf.layers.LSTM;
import org.deeplearning4j.nn.conf.layers.RnnOutputLayer;
import org.deeplearning4j.nn.multilayer.MultiLayerNetwork;
import org.nd4j.linalg.activations.Activation;
import org.nd4j.linalg.api.buffer.DataType;
import org.nd4j.linalg.api.ndarray.INDArray;
import org.nd4j.linalg.dataset.DataSet;
import org.nd4j.linalg.factory.Nd4j;
import org.nd4j.linalg.learning.config.Adam;
import org.nd4j.linalg.lossfunctions.LossFunctions;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/** Direct multi-output LSTM forecast: 30 daily observations to the next 7 days. */
public final class SalesLstmForecaster {
    public static final int DEFAULT_EPOCHS = 30;
    public static final int DEFAULT_HIDDEN_UNITS = 32;
    public static final double DEFAULT_LEARNING_RATE = 0.001;
    public static final int DEFAULT_SEARCH_EPOCHS = 30;
    public static final int DEFAULT_EARLY_STOPPING_PATIENCE = 5;
    private static final long RANDOM_SEED = 12345L;

    private SalesLstmForecaster() { }

    /** Trains on train windows, selects the epoch using validation RMSE, and evaluates test once. */
    public static TrainingResult train(PreprocessingResult prepared) {
        return evaluateTest(fit(prepared, DEFAULT_EPOCHS, DEFAULT_HIDDEN_UNITS,
                DEFAULT_LEARNING_RATE, DEFAULT_EARLY_STOPPING_PATIENCE));
    }

    /** Configurable small-training entry point; test data is not used for epoch selection. */
    public static TrainingResult train(PreprocessingResult prepared, int maxEpochs,
                                       int hiddenUnits, double learningRate) {
        return evaluateTest(fit(prepared, maxEpochs, hiddenUnits, learningRate, maxEpochs));
    }

    /** Train and select an epoch using validation only. Does not access the test split. */
    public static FitResult fit(PreprocessingResult prepared, int maxEpochs,
                                int hiddenUnits, double learningRate, int patience) {
        return fit(prepared, maxEpochs, hiddenUnits, learningRate, patience, RANDOM_SEED);
    }

    /** Fits using the supplied seed; only train and validation are used. */
    public static FitResult fit(PreprocessingResult prepared, int maxEpochs,
                                int hiddenUnits, double learningRate, int patience, long randomSeed) {
        if (prepared == null) { throw new IllegalArgumentException("Preprocessing result is required."); }
        if (maxEpochs <= 0 || hiddenUnits <= 0 || patience <= 0 || !(learningRate > 0.0)
                || !Double.isFinite(learningRate)) {
            throw new IllegalArgumentException("Epochs, hidden units, patience and finite learning rate must be positive.");
        }
        Split train = prepared.getTrain();
        Split validation = prepared.getValidation();
        int inputDays = prepared.getInputDays();
        int forecastDays = prepared.getForecastDays();
        if (inputDays <= 0 || forecastDays <= 0 || train.getWindows().isEmpty()
                || validation.getWindows().isEmpty()) {
            throw new IllegalArgumentException("Train and validation must each contain windows.");
        }

        MultiLayerConfiguration configuration = new NeuralNetConfiguration.Builder()
                .seed(randomSeed)
                .updater(new Adam(learningRate))
                .list()
                .layer(new LSTM.Builder().nIn(SalesPreprocessor.getInputFeatureNames().size()).nOut(hiddenUnits)
                        .activation(Activation.TANH).build())
                .layer(new RnnOutputLayer.Builder(LossFunctions.LossFunction.MSE)
                        .nIn(hiddenUnits).nOut(forecastDays)
                        .activation(Activation.IDENTITY).build())
                .build();
        MultiLayerNetwork network = new MultiLayerNetwork(configuration);
        network.init();

        DataSet trainingData = toDataSet(train.getWindows(), inputDays, forecastDays);
        INDArray validationFeatures = toFeatures(validation.getWindows(), inputDays);
        INDArray bestParameters = null;
        double bestValidationRmse = Double.POSITIVE_INFINITY;
        int bestEpoch = 0;
        int epochsWithoutImprovement = 0;
        List<EpochMetrics> history = new ArrayList<>();

        for (int epoch = 1; epoch <= maxEpochs; epoch++) {
            network.fit(trainingData);
            ForecastMetrics validationMetrics = evaluate(network, validation, validationFeatures, inputDays,
                    forecastDays, prepared.getScaler());
            double validationRmse = validationMetrics.rmse;
            if (!Double.isFinite(validationRmse)) {
                throw new IllegalStateException("Validation RMSE became non-finite at epoch " + epoch);
            }
            history.add(new EpochMetrics(epoch, validationRmse, validationMetrics));
            if (validationRmse < bestValidationRmse - 1e-8) {
                bestValidationRmse = validationRmse;
                bestEpoch = epoch;
                bestParameters = network.params().dup();
                epochsWithoutImprovement = 0;
            } else {
                epochsWithoutImprovement++;
                if (epochsWithoutImprovement >= patience) { break; }
            }
        }

        if (bestParameters == null) {
            throw new IllegalStateException("Training did not produce a finite validation score.");
        }
        network.setParams(bestParameters);
        ForecastMetrics bestValidationMetrics = evaluate(network, validation, validationFeatures,
                inputDays, forecastDays, prepared.getScaler());
        return new FitResult(network, prepared, history.size(), bestEpoch, bestValidationRmse,
                bestValidationMetrics, history, hiddenUnits, learningRate, patience, randomSeed);
    }

    /** Evaluate the selected validation checkpoint on test after all choices are fixed. */
    public static TrainingResult evaluateTest(FitResult fit) {
        if (fit == null) { throw new IllegalArgumentException("Fitted model is required."); }
        PreprocessingResult prepared = fit.prepared;
        Split test = prepared.getTest();
        if (test.getWindows().isEmpty()) {
            throw new IllegalArgumentException("Test split must contain forecast windows.");
        }
        int inputDays = prepared.getInputDays();
        int forecastDays = prepared.getForecastDays();
        INDArray testFeatures = toFeatures(test.getWindows(), inputDays);
        ForecastMetrics testMetrics = evaluate(fit.network, test, testFeatures, inputDays,
                forecastDays, prepared.getScaler());
        return new TrainingResult(fit, testMetrics);
    }

    /**
     * Compare a small, explicit configuration grid using validation RMSE only.
     * The returned winner is fitted but has not had its test split evaluated.
     */
    public static TuningResult tuneOnValidation(PreprocessingResult prepared, int maxEpochs, int patience) {
        List<ModelConfig> configurations = List.of(
                new ModelConfig(16, 0.001),
                new ModelConfig(32, 0.001),
                new ModelConfig(32, 0.0003));
        List<FitResult> candidates = new ArrayList<>();
        FitResult best = null;
        for (ModelConfig config : configurations) {
            FitResult candidate = fit(prepared, maxEpochs, config.hiddenUnits,
                    config.learningRate, patience);
            candidates.add(candidate);
            if (best == null || candidate.bestValidationRmse < best.bestValidationRmse) {
                best = candidate;
            }
        }
        return new TuningResult(best, candidates);
    }

    public static void printTuningSummary(TuningResult result) {
        System.out.println("\n==========================================\nVALIDATION-ONLY LSTM SEARCH\n==========================================");
        System.out.println("Config | Units | Learning rate | Epochs run | Best epoch | Val RMSE");
        for (FitResult candidate : result.candidates) {
            System.out.printf(Locale.ROOT, "%6s | %5d | %13.5f | %10d | %10d | %.4f%n",
                    candidate == result.best ? "SELECT" : "      ", candidate.hiddenUnits,
                    candidate.learningRate, candidate.epochsRun, candidate.bestEpoch,
                    candidate.bestValidationRmse);
        }
        System.out.println("Selected by validation only; no test scores were used in this search.");
        System.out.println("Best model validation errors by horizon (raw sales):");
        printHorizonDiagnostics(result.best.validationMetrics);
        System.out.printf(Locale.ROOT, "Overall | %5d | MAE %.4f | RMSE %.4f%n",
                result.best.validationMetrics.forecastCount, result.best.validationMetrics.mae,
                result.best.validationMetrics.rmse);
        System.out.println("==========================================");
    }

    private static void printHorizonDiagnostics(ForecastMetrics metrics) {
        System.out.println("Horizon | Count | MAE   | RMSE  | Actual mean | Predicted mean | Predicted range");
        for (HorizonMetrics horizon : metrics.horizons) {
            System.out.printf(Locale.ROOT, "%7d | %5d | %.4f | %.4f | %11.4f | %14.4f | %.3f .. %.3f%n",
                    horizon.horizonDays, horizon.forecastCount, horizon.mae, horizon.rmse,
                    horizon.actualMean, horizon.predictionMean, horizon.predictionMin, horizon.predictionMax);
        }
    }

    private static DataSet toDataSet(List<Window> windows, int inputDays, int forecastDays) {
        INDArray features = toFeatures(windows, inputDays);
        INDArray labels = Nd4j.zeros(DataType.FLOAT, windows.size(), forecastDays, inputDays);
        INDArray labelMask = Nd4j.zeros(DataType.FLOAT, windows.size(), inputDays);
        for (int row = 0; row < windows.size(); row++) {
            Window window = windows.get(row);
            requireDimensions(window, inputDays, forecastDays);
            int last = inputDays - 1;
            labelMask.putScalar(new long[]{row, last}, 1.0);
            for (int horizon = 0; horizon < forecastDays; horizon++) {
                labels.putScalar(new long[]{row, horizon, last}, window.getTarget().get(horizon));
            }
        }
        return new DataSet(features, labels, null, labelMask);
    }

    private static INDArray toFeatures(List<Window> windows, int inputDays) {
        int featureCount = SalesPreprocessor.getInputFeatureNames().size();
        INDArray features = Nd4j.zeros(DataType.FLOAT, windows.size(), featureCount, inputDays);
        for (int row = 0; row < windows.size(); row++) {
            List<List<Double>> inputFeatures = windows.get(row).getInputFeatures();
            if (inputFeatures.size() != inputDays) {
                throw new IllegalArgumentException("Window feature length does not match preprocessing configuration.");
            }
            for (int time = 0; time < inputDays; time++) {
                List<Double> featureVector = inputFeatures.get(time);
                if (featureVector.size() != featureCount) {
                    throw new IllegalArgumentException("Window has missing or unexpected input features.");
                }
                for (int feature = 0; feature < featureCount; feature++) {
                    double value = featureVector.get(feature);
                    if (!Double.isFinite(value)) {
                        throw new IllegalArgumentException("Input features must contain only finite values.");
                    }
                    features.putScalar(new long[]{row, feature, time}, value);
                }
            }
        }
        return features;
    }

    private static void requireDimensions(Window window, int inputDays, int forecastDays) {
        if (window.getTarget().size() != forecastDays || window.getRawTarget().size() != forecastDays
                || window.getRawInput().size() != inputDays
                || window.getInputFeatures().size() != inputDays
                || window.getInputFeatures().stream().anyMatch(row ->
                        row.size() != SalesPreprocessor.getInputFeatureNames().size())) {
            throw new IllegalArgumentException("Window date, input and target dimensions are inconsistent.");
        }
    }

    private static ForecastMetrics evaluate(MultiLayerNetwork network, Split split,
                                           INDArray features, int inputDays, int forecastDays,
                                           MinMaxScaler scaler) {
        INDArray sequencePredictions = network.output(features, false);
        if (sequencePredictions.size(0) != split.getWindows().size()
                || sequencePredictions.size(1) != forecastDays
                || sequencePredictions.size(2) != inputDays) {
            throw new IllegalStateException("Unexpected LSTM output shape: "
                    + java.util.Arrays.toString(sequencePredictions.shape()));
        }
        List<Accumulator> byHorizon = new ArrayList<>();
        for (int horizon = 0; horizon < forecastDays; horizon++) { byHorizon.add(new Accumulator()); }
        for (int row = 0; row < split.getWindows().size(); row++) {
            Window window = split.getWindows().get(row);
            requireDimensions(window, inputDays, forecastDays);
            for (int horizon = 0; horizon < forecastDays; horizon++) {
                double normalized = sequencePredictions.getDouble(row, horizon, inputDays - 1);
                double prediction = scaler.inverseTransform(normalized);
                if (!Double.isFinite(prediction)) {
                    throw new IllegalStateException("LSTM produced a non-finite forecast.");
                }
                byHorizon.get(horizon).add(window.getRawTarget().get(horizon), prediction);
            }
        }
        List<HorizonMetrics> horizonMetrics = new ArrayList<>();
        Accumulator overall = new Accumulator();
        for (int h = 0; h < forecastDays; h++) {
            Accumulator accumulator = byHorizon.get(h);
            horizonMetrics.add(new HorizonMetrics(h + 1, accumulator.count,
                    accumulator.mae(), accumulator.rmse(), accumulator.actualMean(),
                    accumulator.predictionMean(), accumulator.predictionMin, accumulator.predictionMax));
            overall.combine(accumulator);
        }
        return new ForecastMetrics(horizonMetrics, overall.count, overall.mae(), overall.rmse());
    }

    public static void printSummary(TrainingResult result) {
        System.out.println("\n==========================================\nDL4J LSTM FORECAST\n==========================================");
        System.out.printf("Training epochs   : %d%n", result.getEpochsRun());
        System.out.printf("Selected epoch    : %d (validation RMSE %.4f)%n",
                result.getBestEpoch(), result.getBestValidationRmse());
        System.out.printf(Locale.ROOT, "Selected model    : %d units, learning rate %.5f, patience %d%n",
                result.fit.hiddenUnits, result.fit.learningRate, result.fit.patience);
        System.out.println("Validation selects the epoch; test is evaluated only after selection.");
        System.out.printf("Test forecasts    : %d rolling-origin predictions%n", result.testMetrics.forecastCount);
        System.out.println("Horizon | Forecasts | MAE      | RMSE");
        for (HorizonMetrics metrics : result.testMetrics.horizons) {
            System.out.printf(Locale.ROOT, "%7d | %9d | %8.4f | %.4f%n",
                    metrics.horizonDays, metrics.forecastCount, metrics.mae, metrics.rmse);
            System.out.printf(Locale.ROOT, "        actual mean %.3f | predicted mean %.3f | predicted range %.3f .. %.3f%n",
                    metrics.actualMean, metrics.predictionMean, metrics.predictionMin, metrics.predictionMax);
        }
        System.out.printf(Locale.ROOT, "Overall | %9d | %8.4f | %.4f%n",
                result.testMetrics.forecastCount, result.testMetrics.mae, result.testMetrics.rmse);
        System.out.println("Metrics are in original sales units; rolling target dates overlap.");
        System.out.println("==========================================");
    }

    private static final class Accumulator {
        private long count;
        private double absoluteError;
        private double squaredError;
        private double actualSum;
        private double predictionSum;
        private double predictionMin = Double.POSITIVE_INFINITY;
        private double predictionMax = Double.NEGATIVE_INFINITY;
        private void add(double actual, double prediction) {
            double error = actual - prediction;
            count++;
            absoluteError += Math.abs(error);
            squaredError += error * error;
            actualSum += actual;
            predictionSum += prediction;
            predictionMin = Math.min(predictionMin, prediction);
            predictionMax = Math.max(predictionMax, prediction);
        }
        private void combine(Accumulator other) {
            count += other.count;
            absoluteError += other.absoluteError;
            squaredError += other.squaredError;
            actualSum += other.actualSum;
            predictionSum += other.predictionSum;
            predictionMin = Math.min(predictionMin, other.predictionMin);
            predictionMax = Math.max(predictionMax, other.predictionMax);
        }
        private double mae() { return absoluteError / count; }
        private double rmse() { return Math.sqrt(squaredError / count); }
        private double actualMean() { return actualSum / count; }
        private double predictionMean() { return predictionSum / count; }
    }

    public static final class HorizonMetrics {
        private final int horizonDays;
        private final long forecastCount;
        private final double mae;
        private final double rmse;
        private final double actualMean;
        private final double predictionMean;
        private final double predictionMin;
        private final double predictionMax;
        private HorizonMetrics(int horizonDays, long forecastCount, double mae, double rmse,
                               double actualMean, double predictionMean, double predictionMin, double predictionMax) {
            this.horizonDays = horizonDays;
            this.forecastCount = forecastCount;
            this.mae = mae;
            this.rmse = rmse;
            this.actualMean = actualMean;
            this.predictionMean = predictionMean;
            this.predictionMin = predictionMin;
            this.predictionMax = predictionMax;
        }
        public int getHorizonDays() { return horizonDays; }
        public long getForecastCount() { return forecastCount; }
        public double getMae() { return mae; }
        public double getRmse() { return rmse; }
        public double getActualMean() { return actualMean; }
        public double getPredictionMean() { return predictionMean; }
        public double getPredictionMin() { return predictionMin; }
        public double getPredictionMax() { return predictionMax; }
    }

    public static final class ForecastMetrics {
        private final List<HorizonMetrics> horizons;
        private final long forecastCount;
        private final double mae;
        private final double rmse;
        private ForecastMetrics(List<HorizonMetrics> horizons, long forecastCount, double mae, double rmse) {
            this.horizons = List.copyOf(horizons);
            this.forecastCount = forecastCount;
            this.mae = mae;
            this.rmse = rmse;
        }
        public List<HorizonMetrics> getHorizons() { return horizons; }
        public long getForecastCount() { return forecastCount; }
        public double getMae() { return mae; }
        public double getRmse() { return rmse; }
    }

    public static final class EpochMetrics {
        private final int epoch;
        private final double validationRmse;
        private final ForecastMetrics validationMetrics;
        private EpochMetrics(int epoch, double validationRmse, ForecastMetrics validationMetrics) {
            this.epoch = epoch;
            this.validationRmse = validationRmse;
            this.validationMetrics = validationMetrics;
        }
        public int getEpoch() { return epoch; }
        public double getValidationRmse() { return validationRmse; }
        public ForecastMetrics getValidationMetrics() { return validationMetrics; }
    }

    public static final class ModelConfig {
        private final int hiddenUnits;
        private final double learningRate;
        public ModelConfig(int hiddenUnits, double learningRate) {
            if (hiddenUnits <= 0 || !(learningRate > 0) || !Double.isFinite(learningRate)) {
                throw new IllegalArgumentException("Model config values must be positive and finite.");
            }
            this.hiddenUnits = hiddenUnits;
            this.learningRate = learningRate;
        }
        public int getHiddenUnits() { return hiddenUnits; }
        public double getLearningRate() { return learningRate; }
    }

    public static final class FitResult {
        private final MultiLayerNetwork network;
        private final PreprocessingResult prepared;
        private final int epochsRun;
        private final int bestEpoch;
        private final double bestValidationRmse;
        private final ForecastMetrics validationMetrics;
        private final List<EpochMetrics> history;
        private final int hiddenUnits;
        private final double learningRate;
        private final int patience;
        private final long randomSeed;
        private FitResult(MultiLayerNetwork network, PreprocessingResult prepared, int epochsRun,
                          int bestEpoch, double bestValidationRmse, ForecastMetrics validationMetrics,
                          List<EpochMetrics> history, int hiddenUnits, double learningRate, int patience,
                          long randomSeed) {
            this.network = network;
            this.prepared = prepared;
            this.epochsRun = epochsRun;
            this.bestEpoch = bestEpoch;
            this.bestValidationRmse = bestValidationRmse;
            this.validationMetrics = validationMetrics;
            this.history = List.copyOf(history);
            this.hiddenUnits = hiddenUnits;
            this.learningRate = learningRate;
            this.patience = patience;
            this.randomSeed = randomSeed;
        }
        public MultiLayerNetwork getNetwork() { return network; }
        public int getEpochsRun() { return epochsRun; }
        public int getBestEpoch() { return bestEpoch; }
        public double getBestValidationRmse() { return bestValidationRmse; }
        public ForecastMetrics getValidationMetrics() { return validationMetrics; }
        public List<EpochMetrics> getHistory() { return history; }
        public int getHiddenUnits() { return hiddenUnits; }
        public double getLearningRate() { return learningRate; }
        public int getPatience() { return patience; }
        public long getRandomSeed() { return randomSeed; }
    }

    public static final class TuningResult {
        private final FitResult best;
        private final List<FitResult> candidates;
        private TuningResult(FitResult best, List<FitResult> candidates) {
            this.best = best;
            this.candidates = List.copyOf(candidates);
        }
        public FitResult getBest() { return best; }
        public List<FitResult> getCandidates() { return candidates; }
    }

    public static final class TrainingResult {
        private final FitResult fit;
        private final ForecastMetrics testMetrics;
        private TrainingResult(FitResult fit, ForecastMetrics testMetrics) {
            this.fit = fit;
            this.testMetrics = testMetrics;
        }
        public MultiLayerNetwork getNetwork() { return fit.network; }
        public PreprocessingResult getPreprocessingResult() { return fit.prepared; }
        public int getEpochsRun() { return fit.epochsRun; }
        public int getBestEpoch() { return fit.bestEpoch; }
        public double getBestValidationRmse() { return fit.bestValidationRmse; }
        public List<EpochMetrics> getHistory() { return fit.history; }
        public FitResult getFitResult() { return fit; }
        public ForecastMetrics getTestMetrics() { return testMetrics; }
    }
}
