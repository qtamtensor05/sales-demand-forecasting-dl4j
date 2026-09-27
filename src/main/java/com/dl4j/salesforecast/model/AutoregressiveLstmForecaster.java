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

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/** One-step LSTM recursively rolled forward to produce a multi-day forecast. */
public final class AutoregressiveLstmForecaster {
    private static final long RANDOM_SEED = 12345L;
    private static final int LAG_DAYS = 7;
    private static final int ROLLING_SHORT_DAYS = 7;
    private static final int ROLLING_LONG_DAYS = 14;

    private AutoregressiveLstmForecaster() { }

    /** Fits and early-stops on recursively generated validation forecasts; does not access test. */
    public static FitResult fit(PreprocessingResult prepared, int maxEpochs,
                                int hiddenUnits, double learningRate, int patience) {
        if (prepared == null) { throw new IllegalArgumentException("Preprocessing result is required."); }
        if (maxEpochs <= 0 || hiddenUnits <= 0 || patience <= 0 || !(learningRate > 0.0)
                || !Double.isFinite(learningRate)) {
            throw new IllegalArgumentException("Epochs, hidden units, patience and finite learning rate must be positive.");
        }
        int inputDays = prepared.getInputDays();
        int forecastDays = prepared.getForecastDays();
        Split train = prepared.getTrain();
        Split validation = prepared.getValidation();
        if (inputDays < ROLLING_LONG_DAYS || forecastDays <= 0
                || train.getWindows().isEmpty() || validation.getWindows().isEmpty()) {
            throw new IllegalArgumentException("Autoregressive LSTM needs windows and at least 14 input days.");
        }

        int featureCount = SalesPreprocessor.getInputFeatureNames().size();
        MultiLayerConfiguration configuration = new NeuralNetConfiguration.Builder()
                .seed(RANDOM_SEED)
                .updater(new Adam(learningRate))
                .list()
                .layer(new LSTM.Builder().nIn(featureCount).nOut(hiddenUnits)
                        .activation(Activation.TANH).build())
                .layer(new RnnOutputLayer.Builder(LossFunctions.LossFunction.MSE)
                        .nIn(hiddenUnits).nOut(1).activation(Activation.IDENTITY).build())
                .build();
        MultiLayerNetwork network = new MultiLayerNetwork(configuration);
        network.init();

        DataSet trainingData = toOneStepDataSet(train.getWindows(), inputDays, featureCount);
        INDArray validationFeatures = toFeatureTensor(validation.getWindows(), inputDays, featureCount);
        INDArray bestParameters = null;
        double bestValidationRmse = Double.POSITIVE_INFINITY;
        int bestEpoch = 0;
        int epochsWithoutImprovement = 0;
        List<EpochMetrics> history = new ArrayList<>();

        for (int epoch = 1; epoch <= maxEpochs; epoch++) {
            network.fit(trainingData);
            ForecastMetrics validationMetrics = evaluate(network, validation, validationFeatures,
                    inputDays, forecastDays, featureCount, prepared.getScaler());
            double rmse = validationMetrics.getRmse();
            if (!Double.isFinite(rmse)) {
                throw new IllegalStateException("Autoregressive validation RMSE became non-finite at epoch " + epoch);
            }
            history.add(new EpochMetrics(epoch, validationMetrics));
            if (rmse < bestValidationRmse - 1e-8) {
                bestValidationRmse = rmse;
                bestEpoch = epoch;
                bestParameters = network.params().dup();
                epochsWithoutImprovement = 0;
            } else if (++epochsWithoutImprovement >= patience) {
                break;
            }
        }

        if (bestParameters == null) {
            throw new IllegalStateException("Autoregressive training produced no finite validation checkpoint.");
        }
        network.setParams(bestParameters);
        ForecastMetrics bestValidationMetrics = evaluate(network, validation, validationFeatures,
                inputDays, forecastDays, featureCount, prepared.getScaler());
        return new FitResult(network, maxEpochs, history.size(), bestEpoch, bestValidationMetrics,
                history, hiddenUnits, learningRate, patience);
    }

    /** Generates rolling-origin forecasts; each prediction is fed into the following step. */
    public static ForecastMetrics evaluate(MultiLayerNetwork network, Split split,
                                           int inputDays, int forecastDays,
                                           MinMaxScaler scaler) {
        if (network == null || split == null || scaler == null || split.getWindows().isEmpty()
                || inputDays <= 0 || forecastDays <= 0) {
            throw new IllegalArgumentException("Model, scaler, positive dimensions and forecast windows are required.");
        }
        return evaluate(network, split, toFeatureTensor(split.getWindows(), inputDays,
                SalesPreprocessor.getInputFeatureNames().size()), inputDays, forecastDays,
                SalesPreprocessor.getInputFeatureNames().size(), scaler);
    }

    private static ForecastMetrics evaluate(MultiLayerNetwork network, Split split, INDArray initialFeatures,
                                            int inputDays, int forecastDays, int featureCount,
                                            MinMaxScaler scaler) {
        List<Window> windows = split.getWindows();
        List<List<List<Double>>> rollingFeatures = new ArrayList<>(windows.size());
        List<List<Double>> rawHistory = new ArrayList<>(windows.size());
        List<Accumulator> byHorizon = new ArrayList<>(forecastDays);
        for (int h = 0; h < forecastDays; h++) { byHorizon.add(new Accumulator()); }
        for (Window window : windows) {
            requireWindow(window, inputDays, forecastDays, featureCount);
            List<List<Double>> sequence = new ArrayList<>(window.getInputFeatures());
            rollingFeatures.add(sequence);
            rawHistory.add(new ArrayList<>(window.getRawInput()));
        }

        INDArray features = initialFeatures;
        for (int horizon = 0; horizon < forecastDays; horizon++) {
            INDArray output = network.output(features, false);
            if (output.size(0) != windows.size() || output.size(1) != 1 || output.size(2) != inputDays) {
                throw new IllegalStateException("Unexpected autoregressive output shape: "
                        + java.util.Arrays.toString(output.shape()));
            }
            for (int row = 0; row < windows.size(); row++) {
                Window window = windows.get(row);
                double normalizedPrediction = output.getDouble(row, 0, inputDays - 1);
                double predictedSales = scaler.inverseTransform(normalizedPrediction);
                double actualSales = window.getRawTarget().get(horizon);
                if (!Double.isFinite(normalizedPrediction) || !Double.isFinite(predictedSales)) {
                    throw new IllegalStateException("Autoregressive model produced a non-finite forecast.");
                }
                byHorizon.get(horizon).add(actualSales, predictedSales);

                LocalDate targetDate = window.getTargetDates().get(horizon);
                List<Double> history = rawHistory.get(row);
                List<List<Double>> sequence = rollingFeatures.get(row);
                sequence.remove(0);
                sequence.add(createFeatureVector(targetDate, predictedSales, history, scaler));
                history.add(predictedSales);
            }
            features = toFeatureTensorFromSequences(rollingFeatures, inputDays, featureCount);
        }

        List<HorizonMetrics> horizonMetrics = new ArrayList<>(forecastDays);
        Accumulator overall = new Accumulator();
        for (int h = 0; h < forecastDays; h++) {
            Accumulator values = byHorizon.get(h);
            horizonMetrics.add(new HorizonMetrics(h + 1, values.count, values.mae(), values.rmse()));
            overall.combine(values);
        }
        return new ForecastMetrics(horizonMetrics, overall.count, overall.mae(), overall.rmse());
    }

    private static List<Double> createFeatureVector(LocalDate date, double currentSales,
                                                    List<Double> previousSales, MinMaxScaler scaler) {
        if (previousSales.size() < ROLLING_LONG_DAYS || previousSales.size() < LAG_DAYS) {
            throw new IllegalStateException("Autoregressive history is too short to compute input features.");
        }
        double rolling7 = tailMean(previousSales, ROLLING_SHORT_DAYS - 1, currentSales);
        double rolling14 = tailMean(previousSales, ROLLING_LONG_DAYS - 1, currentSales);
        double lag7 = previousSales.get(previousSales.size() - LAG_DAYS);
        int dayOfWeek = date.getDayOfWeek().getValue() - 1;
        double angle = 2.0 * Math.PI * dayOfWeek / 7.0;
        return List.of(scaler.transform(currentSales), scaler.transform(lag7), scaler.transform(rolling7),
                scaler.transform(rolling14), Math.sin(angle), Math.cos(angle));
    }

    private static double tailMean(List<Double> previousSales, int previousCount, double currentSales) {
        double sum = currentSales;
        for (int offset = 1; offset <= previousCount; offset++) {
            sum += previousSales.get(previousSales.size() - offset);
        }
        return sum / (previousCount + 1);
    }

    private static DataSet toOneStepDataSet(List<Window> windows, int inputDays, int featureCount) {
        INDArray features = toFeatureTensor(windows, inputDays, featureCount);
        INDArray labels = Nd4j.zeros(DataType.FLOAT, windows.size(), 1, inputDays);
        INDArray labelMask = Nd4j.zeros(DataType.FLOAT, windows.size(), inputDays);
        int last = inputDays - 1;
        for (int row = 0; row < windows.size(); row++) {
            Window window = windows.get(row);
            requireWindow(window, inputDays, window.getTarget().size(), featureCount);
            labels.putScalar(new long[]{row, 0, last}, window.getTarget().get(0));
            labelMask.putScalar(new long[]{row, last}, 1.0);
        }
        return new DataSet(features, labels, null, labelMask);
    }

    private static INDArray toFeatureTensor(List<Window> windows, int inputDays, int featureCount) {
        List<List<List<Double>>> rows = new ArrayList<>(windows.size());
        for (Window window : windows) { rows.add(window.getInputFeatures()); }
        return toFeatureTensorFromSequences(rows, inputDays, featureCount);
    }

    private static INDArray toFeatureTensorFromSequences(List<List<List<Double>>> sequences,
                                                         int inputDays, int featureCount) {
        INDArray features = Nd4j.zeros(DataType.FLOAT, sequences.size(), featureCount, inputDays);
        for (int row = 0; row < sequences.size(); row++) {
            List<List<Double>> sequence = sequences.get(row);
            if (sequence.size() != inputDays) {
                throw new IllegalArgumentException("Autoregressive input length does not match configuration.");
            }
            for (int time = 0; time < inputDays; time++) {
                List<Double> vector = sequence.get(time);
                if (vector.size() != featureCount) {
                    throw new IllegalArgumentException("Autoregressive input feature count does not match configuration.");
                }
                for (int feature = 0; feature < featureCount; feature++) {
                    double value = vector.get(feature);
                    if (!Double.isFinite(value)) {
                        throw new IllegalArgumentException("Autoregressive input features must be finite.");
                    }
                    features.putScalar(new long[]{row, feature, time}, value);
                }
            }
        }
        return features;
    }

    private static void requireWindow(Window window, int inputDays, int forecastDays, int featureCount) {
        if (window.getInputFeatures().size() != inputDays || window.getRawInput().size() != inputDays
                || window.getTarget().size() < forecastDays || window.getRawTarget().size() < forecastDays
                || window.getTargetDates().size() < forecastDays
                || window.getInputFeatures().stream().anyMatch(vector -> vector.size() != featureCount)) {
            throw new IllegalArgumentException("Window dimensions do not match autoregressive configuration.");
        }
    }

    public static void printSummary(FitResult result) {
        System.out.println("\n==========================================\nAUTOREGRESSIVE LSTM (VALIDATION)\n==========================================");
        System.out.printf(Locale.ROOT, "Units / learning rate : %d / %.5f%n", result.hiddenUnits, result.learningRate);
        System.out.printf("Epochs / best epoch   : %d / %d%n", result.epochsRun, result.bestEpoch);
        System.out.printf(Locale.ROOT, "Validation MAE / RMSE: %.4f / %.4f%n",
                result.validationMetrics.mae, result.validationMetrics.rmse);
        System.out.println("Recursive rollout; each predicted day feeds the next forecast step.");
        System.out.println("Horizon | Forecasts | MAE      | RMSE");
        for (HorizonMetrics metrics : result.validationMetrics.horizons) {
            System.out.printf(Locale.ROOT, "%7d | %9d | %8.4f | %.4f%n",
                    metrics.horizonDays, metrics.forecastCount, metrics.mae, metrics.rmse);
        }
        System.out.println("==========================================");
    }

    public static void printComparison(SalesLstmForecaster.ForecastMetrics directMetrics,
                                       com.dl4j.salesforecast.evaluation.WeeklyNaiveBaseline.BaselineResult naive,
                                       ForecastMetrics autoregressive) {
        System.out.println("\n==========================================\nVALIDATION ARCHITECTURE COMPARISON\n==========================================");
        System.out.println("Method                      | Forecasts | MAE      | RMSE");
        System.out.printf(Locale.ROOT, "%-28s | %9d | %8.4f | %.4f%n",
                "Weekly naive", naive.getForecastCount(), naive.getMae(), naive.getRmse());
        System.out.printf(Locale.ROOT, "%-28s | %9d | %8.4f | %.4f%n",
                "Direct 7-output LSTM", directMetrics.getForecastCount(), directMetrics.getMae(), directMetrics.getRmse());
        System.out.printf(Locale.ROOT, "%-28s | %9d | %8.4f | %.4f%n",
                "Autoregressive LSTM", autoregressive.forecastCount, autoregressive.mae, autoregressive.rmse);
        String best = "Weekly naive";
        double bestRmse = naive.getRmse();
        if (directMetrics.getRmse() < bestRmse) {
            best = "Direct 7-output LSTM";
            bestRmse = directMetrics.getRmse();
        }
        if (autoregressive.rmse < bestRmse) { best = "Autoregressive LSTM"; }
        System.out.println("Lowest validation RMSE: " + best);
        System.out.println("Selection uses validation only; test metrics are not computed here.");
        System.out.println("==========================================");
    }

    public static final class FitResult {
        private final MultiLayerNetwork network;
        private final int maxEpochs;
        private final int epochsRun;
        private final int bestEpoch;
        private final ForecastMetrics validationMetrics;
        private final List<EpochMetrics> history;
        private final int hiddenUnits;
        private final double learningRate;
        private final int patience;
        private FitResult(MultiLayerNetwork network, int maxEpochs, int epochsRun, int bestEpoch,
                          ForecastMetrics validationMetrics, List<EpochMetrics> history,
                          int hiddenUnits, double learningRate, int patience) {
            this.network = network;
            this.maxEpochs = maxEpochs;
            this.epochsRun = epochsRun;
            this.bestEpoch = bestEpoch;
            this.validationMetrics = validationMetrics;
            this.history = List.copyOf(history);
            this.hiddenUnits = hiddenUnits;
            this.learningRate = learningRate;
            this.patience = patience;
        }
        public MultiLayerNetwork getNetwork() { return network; }
        public int getMaxEpochs() { return maxEpochs; }
        public int getEpochsRun() { return epochsRun; }
        public int getBestEpoch() { return bestEpoch; }
        public ForecastMetrics getValidationMetrics() { return validationMetrics; }
        public List<EpochMetrics> getHistory() { return history; }
        public int getHiddenUnits() { return hiddenUnits; }
        public double getLearningRate() { return learningRate; }
        public int getPatience() { return patience; }
    }

    public static final class EpochMetrics {
        private final int epoch;
        private final ForecastMetrics validationMetrics;
        private EpochMetrics(int epoch, ForecastMetrics validationMetrics) {
            this.epoch = epoch;
            this.validationMetrics = validationMetrics;
        }
        public int getEpoch() { return epoch; }
        public ForecastMetrics getValidationMetrics() { return validationMetrics; }
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
}
