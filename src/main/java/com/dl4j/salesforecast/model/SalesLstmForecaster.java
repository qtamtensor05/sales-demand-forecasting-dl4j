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
    private static final long RANDOM_SEED = 12345L;

    private SalesLstmForecaster() { }

    /** Trains on train windows, selects the epoch using validation RMSE, and evaluates test once. */
    public static TrainingResult train(PreprocessingResult prepared) {
        return train(prepared, DEFAULT_EPOCHS, DEFAULT_HIDDEN_UNITS, DEFAULT_LEARNING_RATE);
    }

    /** Configurable small-training entry point; test data is not used for epoch selection. */
    public static TrainingResult train(PreprocessingResult prepared, int maxEpochs,
                                       int hiddenUnits, double learningRate) {
        if (prepared == null) { throw new IllegalArgumentException("Preprocessing result is required."); }
        if (maxEpochs <= 0 || hiddenUnits <= 0 || !(learningRate > 0.0)
                || !Double.isFinite(learningRate)) {
            throw new IllegalArgumentException("Epochs, hidden units and learning rate must be positive and finite.");
        }
        Split train = prepared.getTrain();
        Split validation = prepared.getValidation();
        Split test = prepared.getTest();
        int inputDays = prepared.getInputDays();
        int forecastDays = prepared.getForecastDays();
        if (inputDays <= 0 || forecastDays <= 0 || train.getWindows().isEmpty()
                || validation.getWindows().isEmpty() || test.getWindows().isEmpty()) {
            throw new IllegalArgumentException("Train, validation, and test must each contain windows.");
        }

        MultiLayerConfiguration configuration = new NeuralNetConfiguration.Builder()
                .seed(RANDOM_SEED)
                .updater(new Adam(learningRate))
                .list()
                .layer(new LSTM.Builder().nIn(1).nOut(hiddenUnits)
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
        List<EpochMetrics> history = new ArrayList<>();

        for (int epoch = 1; epoch <= maxEpochs; epoch++) {
            network.fit(trainingData);
            double validationRmse = evaluate(network, validation, validationFeatures, inputDays,
                    forecastDays, prepared.getScaler()).rmse;
            history.add(new EpochMetrics(epoch, validationRmse));
            if (validationRmse < bestValidationRmse) {
                bestValidationRmse = validationRmse;
                bestEpoch = epoch;
                bestParameters = network.params().dup();
            }
        }

        if (bestParameters == null) {
            throw new IllegalStateException("Training did not produce a finite validation score.");
        }
        network.setParams(bestParameters);
        INDArray testFeatures = toFeatures(test.getWindows(), inputDays);
        ForecastMetrics testMetrics = evaluate(network, test, testFeatures, inputDays,
                forecastDays, prepared.getScaler());
        return new TrainingResult(network, prepared, maxEpochs, bestEpoch,
                bestValidationRmse, history, testMetrics);
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
        INDArray features = Nd4j.zeros(DataType.FLOAT, windows.size(), 1, inputDays);
        for (int row = 0; row < windows.size(); row++) {
            List<Double> input = windows.get(row).getInput();
            if (input.size() != inputDays) {
                throw new IllegalArgumentException("Window input length does not match preprocessing configuration.");
            }
            for (int time = 0; time < inputDays; time++) {
                features.putScalar(new long[]{row, 0, time}, input.get(time));
            }
        }
        return features;
    }

    private static void requireDimensions(Window window, int inputDays, int forecastDays) {
        if (window.getTarget().size() != forecastDays || window.getRawTarget().size() != forecastDays
                || window.getRawInput().size() != inputDays) {
            throw new IllegalArgumentException("Window date, input and target dimensions are inconsistent.");
        }
    }

    private static ForecastMetrics evaluate(MultiLayerNetwork network, Split split,
                                           INDArray features, int inputDays, int forecastDays,
                                           MinMaxScaler scaler) {
        INDArray sequencePredictions = network.output(features, false);
        List<Accumulator> byHorizon = new ArrayList<>();
        for (int horizon = 0; horizon < forecastDays; horizon++) { byHorizon.add(new Accumulator()); }
        for (int row = 0; row < split.getWindows().size(); row++) {
            Window window = split.getWindows().get(row);
            requireDimensions(window, inputDays, forecastDays);
            for (int horizon = 0; horizon < forecastDays; horizon++) {
                double normalized = sequencePredictions.getDouble(row, horizon, inputDays - 1);
                double prediction = scaler.inverseTransform(normalized);
                byHorizon.get(horizon).add(window.getRawTarget().get(horizon), prediction);
            }
        }
        List<HorizonMetrics> horizonMetrics = new ArrayList<>();
        Accumulator overall = new Accumulator();
        for (int h = 0; h < forecastDays; h++) {
            Accumulator accumulator = byHorizon.get(h);
            horizonMetrics.add(new HorizonMetrics(h + 1, accumulator.count,
                    accumulator.mae(), accumulator.rmse()));
            overall.combine(accumulator);
        }
        return new ForecastMetrics(horizonMetrics, overall.count, overall.mae(), overall.rmse());
    }

    public static void printSummary(TrainingResult result) {
        System.out.println("\n==========================================\nDL4J LSTM FORECAST\n==========================================");
        System.out.printf("Training epochs   : %d%n", result.epochsRun);
        System.out.printf("Selected epoch    : %d (validation RMSE %.4f)%n",
                result.bestEpoch, result.bestValidationRmse);
        System.out.println("Validation selects the epoch; test is evaluated only after selection.");
        System.out.printf("Test forecasts    : %d rolling-origin predictions%n", result.testMetrics.forecastCount);
        System.out.println("Horizon | Forecasts | MAE      | RMSE");
        for (HorizonMetrics metrics : result.testMetrics.horizons) {
            System.out.printf(Locale.ROOT, "%7d | %9d | %8.4f | %.4f%n",
                    metrics.horizonDays, metrics.forecastCount, metrics.mae, metrics.rmse);
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
        private void add(double actual, double prediction) {
            double error = actual - prediction;
            count++;
            absoluteError += Math.abs(error);
            squaredError += error * error;
        }
        private void combine(Accumulator other) {
            count += other.count;
            absoluteError += other.absoluteError;
            squaredError += other.squaredError;
        }
        private double mae() { return absoluteError / count; }
        private double rmse() { return Math.sqrt(squaredError / count); }
    }

    public static final class HorizonMetrics {
        private final int horizonDays;
        private final long forecastCount;
        private final double mae;
        private final double rmse;
        private HorizonMetrics(int horizonDays, long forecastCount, double mae, double rmse) {
            this.horizonDays = horizonDays;
            this.forecastCount = forecastCount;
            this.mae = mae;
            this.rmse = rmse;
        }
        public int getHorizonDays() { return horizonDays; }
        public long getForecastCount() { return forecastCount; }
        public double getMae() { return mae; }
        public double getRmse() { return rmse; }
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
        private EpochMetrics(int epoch, double validationRmse) {
            this.epoch = epoch;
            this.validationRmse = validationRmse;
        }
        public int getEpoch() { return epoch; }
        public double getValidationRmse() { return validationRmse; }
    }

    public static final class TrainingResult {
        private final MultiLayerNetwork network;
        private final PreprocessingResult prepared;
        private final int epochsRun;
        private final int bestEpoch;
        private final double bestValidationRmse;
        private final List<EpochMetrics> history;
        private final ForecastMetrics testMetrics;
        private TrainingResult(MultiLayerNetwork network, PreprocessingResult prepared,
                               int epochsRun, int bestEpoch, double bestValidationRmse,
                               List<EpochMetrics> history, ForecastMetrics testMetrics) {
            this.network = network;
            this.prepared = prepared;
            this.epochsRun = epochsRun;
            this.bestEpoch = bestEpoch;
            this.bestValidationRmse = bestValidationRmse;
            this.history = List.copyOf(history);
            this.testMetrics = testMetrics;
        }
        public MultiLayerNetwork getNetwork() { return network; }
        public PreprocessingResult getPreprocessingResult() { return prepared; }
        public int getEpochsRun() { return epochsRun; }
        public int getBestEpoch() { return bestEpoch; }
        public double getBestValidationRmse() { return bestValidationRmse; }
        public List<EpochMetrics> getHistory() { return history; }
        public ForecastMetrics getTestMetrics() { return testMetrics; }
    }
}
