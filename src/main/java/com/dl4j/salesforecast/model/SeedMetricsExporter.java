package com.dl4j.salesforecast.model;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Locale;

public final class SeedMetricsExporter {
    private SeedMetricsExporter() { }

    public static void writeCsv(MultiSeriesExperimentRunner.MultiSeriesResult result, Path path) throws IOException {
        if (result.getSeriesResults().stream().flatMap(s -> s.getSeedResults().stream())
                .anyMatch(s -> s.getResidualHuber() != null)) {
            writeHuberCsv(result, path);
            return;
        }
        boolean residual = result.getSeriesResults().stream().flatMap(s -> s.getSeedResults().stream())
                .anyMatch(s -> s.getResidual() != null);
        StringBuilder csv = new StringBuilder("stratum,store,item,seed,direct_mae,direct_rmse,direct_best_epoch,"
                + "direct_epochs_run,ar_mae,ar_rmse,ar_best_epoch,ar_epochs_run"
                + (residual ? ",residual_mae,residual_rmse,residual_best_epoch,residual_epochs_run" : "") + "\n");
        for (MultiSeriesExperimentRunner.SeriesResult series : result.getSeriesResults()) {
            for (ExperimentRunner.SeedResult seed : series.getSeedResults()) {
                csv.append(String.format(Locale.ROOT,
                        "%s,%d,%d,%d,%.10f,%.10f,%d,%d,%.10f,%.10f,%d,%d",
                        series.getStratum(), series.getStoreId(), series.getItemId(), seed.getSeed(),
                        seed.getDirectMae(), seed.getDirectRmse(), seed.getDirectBestEpoch(),
                        seed.getDirectEpochsRun(), seed.getAutoregressiveMetrics().getMae(),
                        seed.getAutoregressiveMetrics().getRmse(), seed.getAutoregressiveBestEpoch(),
                        seed.getAutoregressiveEpochsRun()));
                if (residual) {
                    var fit = seed.getResidual();
                    if (fit == null) { csv.append(",,,,"); }
                    else { csv.append(String.format(Locale.ROOT, ",%.10f,%.10f,%d,%d",
                            fit.getValidationMetrics().getMae(), fit.getValidationMetrics().getRmse(),
                            fit.getBestEpoch(), fit.getEpochsRun())); }
                }
                csv.append('\n');
            }
        }
        Files.writeString(path, csv, StandardCharsets.UTF_8);
    }

    private static void writeHuberCsv(MultiSeriesExperimentRunner.MultiSeriesResult result, Path path) throws IOException {
        StringBuilder csv = new StringBuilder("stratum,store,item,seed,residual_mae,residual_rmse,residual_best_epoch,residual_epochs_run,"
                + "huber_mae,huber_rmse,huber_best_epoch,huber_epochs_run\n");
        for (var series : result.getSeriesResults()) {
            for (var seed : series.getSeedResults()) {
                csv.append(String.format(Locale.ROOT, "%s,%d,%d,%d", series.getStratum(), series.getStoreId(), series.getItemId(), seed.getSeed()));
                for (var fit : java.util.List.of(seed.getResidual(), seed.getResidualHuber())) {
                    csv.append(String.format(Locale.ROOT, ",%.10f,%.10f,%d,%d", fit.getValidationMetrics().getMae(),
                            fit.getValidationMetrics().getRmse(), fit.getBestEpoch(), fit.getEpochsRun()));
                }
                csv.append('\n');
            }
        }
        Files.writeString(path, csv, StandardCharsets.UTF_8);
    }
}
