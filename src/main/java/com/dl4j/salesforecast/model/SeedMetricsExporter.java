package com.dl4j.salesforecast.model;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Locale;

public final class SeedMetricsExporter {
    private SeedMetricsExporter() { }

    public static void writeCsv(MultiSeriesExperimentRunner.MultiSeriesResult result, Path path) throws IOException {
        StringBuilder csv = new StringBuilder("stratum,store,item,seed,direct_mae,direct_rmse,direct_best_epoch,"
                + "direct_epochs_run,ar_mae,ar_rmse,ar_best_epoch,ar_epochs_run\n");
        for (MultiSeriesExperimentRunner.SeriesResult series : result.getSeriesResults()) {
            for (ExperimentRunner.SeedResult seed : series.getSeedResults()) {
                csv.append(String.format(Locale.ROOT,
                        "%s,%d,%d,%d,%.10f,%.10f,%d,%d,%.10f,%.10f,%d,%d%n",
                        series.getStratum(), series.getStoreId(), series.getItemId(), seed.getSeed(),
                        seed.getDirectMae(), seed.getDirectRmse(), seed.getDirectBestEpoch(),
                        seed.getDirectEpochsRun(), seed.getAutoregressiveMetrics().getMae(),
                        seed.getAutoregressiveMetrics().getRmse(), seed.getAutoregressiveBestEpoch(),
                        seed.getAutoregressiveEpochsRun()));
            }
        }
        Files.writeString(path, csv, StandardCharsets.UTF_8);
    }
}
