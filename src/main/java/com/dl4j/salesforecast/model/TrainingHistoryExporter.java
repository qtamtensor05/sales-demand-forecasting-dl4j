package com.dl4j.salesforecast.model;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/** Exports per-epoch train/validation losses for plotting. */
public final class TrainingHistoryExporter {
    private static final String HEADER = "stratum,store,item,model,seed,epoch,"
            + "train_loss_scaled_mse,val_loss_scaled_mse,val_rmse_sales,is_best_epoch\n";

    private TrainingHistoryExporter() { }

    public static String formatCsv(MultiSeriesExperimentRunner.MultiSeriesResult result) {
        if (result == null) { throw new IllegalArgumentException("Multi-series result is required."); }
        List<Row> rows = new ArrayList<>();
        for (MultiSeriesExperimentRunner.SeriesResult series : result.getSeriesResults()) {
            for (ExperimentRunner.SeedResult seed : series.getSeedResults()) {
                for (SalesLstmForecaster.EpochMetrics epoch : seed.getDirectHistory()) {
                    rows.add(new Row(series.getStratum(), series.getStoreId(), series.getItemId(),
                            "Direct", seed.getSeed(), epoch.getEpoch(), epoch.getTrainingLoss(),
                            epoch.getValidationLoss(), epoch.getValidationRmse(),
                            epoch.getEpoch() == seed.getDirectBestEpoch()));
                }
                for (AutoregressiveLstmForecaster.EpochMetrics epoch : seed.getAutoregressiveHistory()) {
                    rows.add(new Row(series.getStratum(), series.getStoreId(), series.getItemId(),
                            "AR", seed.getSeed(), epoch.getEpoch(), epoch.getTrainingLoss(),
                            epoch.getValidationLoss(), epoch.getValidationMetrics().getRmse(),
                            epoch.getEpoch() == seed.getAutoregressiveBestEpoch()));
                }
            }
        }
        return formatRows(rows);
    }

    static String formatRows(List<Row> rows) {
        if (rows == null || rows.stream().anyMatch(row -> row == null)) {
            throw new IllegalArgumentException("History rows must be non-null.");
        }
        StringBuilder csv = new StringBuilder(HEADER);
        for (Row row : rows) {
            csv.append(String.format(Locale.ROOT, "%s,%d,%d,%s,%d,%d,%.10f,%.10f,%.10f,%s\n",
                    row.stratum, row.storeId, row.itemId, row.model, row.seed, row.epoch,
                    row.trainingLoss, row.validationLoss, row.validationRmse, row.bestEpoch));
        }
        return csv.toString();
    }

    public static void writeCsv(MultiSeriesExperimentRunner.MultiSeriesResult result, Path outputPath)
            throws IOException {
        if (outputPath == null) { throw new IllegalArgumentException("Output path is required."); }
        Path parent = outputPath.toAbsolutePath().getParent();
        if (parent != null) { Files.createDirectories(parent); }
        Files.writeString(outputPath, formatCsv(result), StandardCharsets.UTF_8);
    }

    static final class Row {
        private final String stratum;
        private final int storeId;
        private final int itemId;
        private final String model;
        private final long seed;
        private final int epoch;
        private final double trainingLoss;
        private final double validationLoss;
        private final double validationRmse;
        private final boolean bestEpoch;

        Row(String stratum, int storeId, int itemId, String model, long seed, int epoch,
            double trainingLoss, double validationLoss, double validationRmse, boolean bestEpoch) {
            if (stratum == null || stratum.isBlank() || model == null || model.isBlank()
                    || storeId <= 0 || itemId <= 0 || epoch <= 0
                    || !Double.isFinite(trainingLoss) || !Double.isFinite(validationLoss)
                    || !Double.isFinite(validationRmse)) {
                throw new IllegalArgumentException("A finite, positive-identity history row is required.");
            }
            this.stratum = stratum;
            this.storeId = storeId;
            this.itemId = itemId;
            this.model = model;
            this.seed = seed;
            this.epoch = epoch;
            this.trainingLoss = trainingLoss;
            this.validationLoss = validationLoss;
            this.validationRmse = validationRmse;
            this.bestEpoch = bestEpoch;
        }
    }
}
