package com.dl4j.salesforecast.model;

import java.io.BufferedWriter;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Locale;

public final class ValidationPredictionExporter {
    private ValidationPredictionExporter() { }

    public static void writeCsv(MultiSeriesExperimentRunner.MultiSeriesResult result, Path path) throws IOException {
        try (BufferedWriter out = Files.newBufferedWriter(path, StandardCharsets.UTF_8)) {
            out.write("stratum,store,item,model,seed,origin_date,target_date,horizon,actual,prediction,weekly_naive\n");
            for (MultiSeriesExperimentRunner.SeriesResult series : result.getSeriesResults()) {
                for (ExperimentRunner.SeedResult seed : series.getSeedResults()) {
                    write(out, series, seed.getSeed(), "Direct", seed.getDirectMetrics().getPredictions());
                    write(out, series, seed.getSeed(), "AR", seed.getAutoregressiveMetrics().getPredictions());
                }
            }
        }
    }

    private static void write(BufferedWriter out, MultiSeriesExperimentRunner.SeriesResult series,
                              long seed, String model, List<ValidationPrediction> predictions) throws IOException {
        for (ValidationPrediction row : predictions) {
            out.write(String.format(Locale.ROOT, "%s,%d,%d,%s,%d,%s,%s,%d,%.10f,%.10f,%.10f%n",
                    series.getStratum(), series.getStoreId(), series.getItemId(), model, seed,
                    row.getOriginDate(), row.getTargetDate(), row.getHorizon(), row.getActual(),
                    row.getPrediction(), row.getWeeklyNaive()));
        }
    }
}
