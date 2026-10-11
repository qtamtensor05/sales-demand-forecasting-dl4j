package com.dl4j.salesforecast.model;

import java.util.Locale;

/** Immutable context used to report completed epochs in a multi-series experiment. */
public final class TrainingProgress {
    private final int fitNumber;
    private final int totalFits;
    private final String model;
    private final String stratum;
    private final int storeId;
    private final int itemId;
    private final long seed;
    private final int maxEpochs;

    private TrainingProgress(int fitNumber, int totalFits, String model, String stratum,
                             int storeId, int itemId, long seed, int maxEpochs) {
        this.fitNumber = fitNumber;
        this.totalFits = totalFits;
        this.model = model;
        this.stratum = stratum;
        this.storeId = storeId;
        this.itemId = itemId;
        this.seed = seed;
        this.maxEpochs = maxEpochs;
    }

    public static TrainingProgress forFit(int seriesIndex, int totalSeries,
                                          int seedIndex, int seedsPerSeries,
                                          boolean direct, String stratum,
                                          int storeId, int itemId, long seed, int maxEpochs) {
        return forFit(seriesIndex, totalSeries, seedIndex, seedsPerSeries, direct ? 0 : 1,
                java.util.List.of("Direct", "AR"), stratum, storeId, itemId, seed, maxEpochs);
    }

    public static TrainingProgress forFit(int seriesIndex, int totalSeries, int seedIndex,
            int seedsPerSeries, int modelIndex, java.util.List<String> models, String stratum,
            int storeId, int itemId, long seed, int maxEpochs) {
        if (models == null || modelIndex < 0 || modelIndex >= models.size()) {
            throw new IllegalArgumentException("Valid model index is required.");
        }
        if (seriesIndex < 0 || seriesIndex >= totalSeries || totalSeries <= 0
                || seedIndex < 0 || seedIndex >= seedsPerSeries || seedsPerSeries <= 0
                || stratum == null || stratum.isBlank() || storeId <= 0 || itemId <= 0
                || maxEpochs <= 0) {
            throw new IllegalArgumentException("Valid series, seed, model and epoch context is required.");
        }
        int fitsPerSeries = seedsPerSeries * models.size();
        int fitNumber = seriesIndex * fitsPerSeries + seedIndex * models.size() + modelIndex + 1;
        return new TrainingProgress(fitNumber, totalSeries * fitsPerSeries,
                models.get(modelIndex), stratum, storeId, itemId, seed, maxEpochs);
    }

    public String formatCompletedEpoch(int epoch) {
        if (epoch <= 0 || epoch > maxEpochs) {
            throw new IllegalArgumentException("Completed epoch must be within the configured epoch range.");
        }
        return String.format(Locale.ROOT,
                "[%d/%d] %s | %s | store=%d item=%d | seed=%d | epoch=%d/%d",
                fitNumber, totalFits, model, stratum, storeId, itemId, seed, epoch, maxEpochs);
    }

    public void printCompletedEpoch(int epoch) {
        System.out.println(formatCompletedEpoch(epoch));
    }

    public void printCompletedEpoch(int epoch, double trainingLoss, double validationLoss,
                                    double validationRmse) {
        System.out.printf(Locale.ROOT, "%s | trainLoss=%.8f | valLoss=%.8f | valRMSE=%.4f%n",
                formatCompletedEpoch(epoch), trainingLoss, validationLoss, validationRmse);
    }

    public int getFitNumber() { return fitNumber; }
}
