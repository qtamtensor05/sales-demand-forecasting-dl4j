package com.dl4j.salesforecast.model;

import com.dl4j.salesforecast.preprocessing.SalesPreprocessor.MinMaxScaler;
import com.dl4j.salesforecast.preprocessing.SalesPreprocessor.PreprocessingResult;
import com.dl4j.salesforecast.preprocessing.SalesPreprocessor.Split;
import com.dl4j.salesforecast.preprocessing.SalesPreprocessor.Window;
import org.deeplearning4j.nn.multilayer.MultiLayerNetwork;

/** Weekly-anchored AR using the same architecture, inputs, MSE and checkpoint protocol as absolute AR. */
public final class ResidualLstmForecaster {
    private ResidualLstmForecaster() { }

    public static AutoregressiveLstmForecaster.FitResult fit(PreprocessingResult prepared,
            int maxEpochs, int hiddenUnits, double learningRate, int patience, long seed,
            TrainingProgress progress) {
        return AutoregressiveLstmForecaster.fit(prepared, maxEpochs, hiddenUnits,
                learningRate, patience, seed, progress, true);
    }

    public static AutoregressiveLstmForecaster.ForecastMetrics evaluate(MultiLayerNetwork network,
            Split split, int inputDays, int forecastDays, MinMaxScaler scaler) {
        return AutoregressiveLstmForecaster.evaluate(network, split, inputDays, forecastDays, scaler, true);
    }

    public static AutoregressiveLstmForecaster.FitResult fit(PreprocessingResult prepared,
            int maxEpochs, int hiddenUnits, double learningRate, int patience, long seed,
            TrainingProgress progress, LossMode lossMode) {
        return AutoregressiveLstmForecaster.fit(prepared, maxEpochs, hiddenUnits,
                learningRate, patience, seed, progress, true, lossMode);
    }

    /** h is zero-based; every anchor for H1..H7 is observed at the original forecast origin. */
    public static double baseline(Window window, int h) {
        if (window == null || h < 0 || h >= 7 || window.getRawInput().size() < 7
                || h >= window.getTargetDates().size()) {
            throw new IllegalArgumentException("Weekly residual requires observed history and horizon 1..7.");
        }
        int index = window.getRawInput().size() - 7 + h;
        if (!window.getInputDates().get(index).plusDays(7).equals(window.getTargetDates().get(h))) {
            throw new IllegalArgumentException("Weekly anchor date is not target date minus seven days.");
        }
        return window.getRawInput().get(index);
    }

    /** Difference of two values scaled by the SAME train-only sales scaler; its offset cancels. */
    public static double scaledTarget(Window window, int h, MinMaxScaler scaler) {
        return (window.getRawTarget().get(h) - baseline(window, h)) / scaler.getScale();
    }

    public static double reconstruct(double baseline, double scaledResidual, MinMaxScaler scaler) {
        double prediction = baseline + scaledResidual * scaler.getScale();
        if (!Double.isFinite(prediction)) {
            throw new IllegalStateException("Residual model produced a non-finite forecast.");
        }
        return prediction;
    }
}
