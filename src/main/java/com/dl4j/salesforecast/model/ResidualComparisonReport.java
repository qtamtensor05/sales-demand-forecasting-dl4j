package com.dl4j.salesforecast.model;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.function.Predicate;
import java.util.stream.Collectors;

/** Four-model comparison: mean seeds within series, then unweighted mean across series. */
public final class ResidualComparisonReport {
    private static final List<String> MODELS = List.of("Weekly", "Direct", "AR", "ResidualAR");
    private ResidualComparisonReport() { }

    public static String format(MultiSeriesExperimentRunner.MultiSeriesResult result) {
        List<Row> rows = new ArrayList<>();
        for (var series : result.getSeriesResults()) {
            for (var seed : series.getSeedResults()) {
                if (seed.getResidual() == null) {
                    throw new IllegalArgumentException("Residual comparison requires all residual fits.");
                }
                var weekly = series.getWeeklyMetrics();
                var direct = seed.getDirectMetrics();
                var ar = seed.getAutoregressiveMetrics();
                var residual = seed.getResidual().getValidationMetrics();
                for (int h = 0; h <= weekly.getHorizons().size(); h++) {
                    rows.add(new Row(series.getStratum(), series.getStoreId(), series.getItemId(), seed.getSeed(), h,
                            new double[]{h == 0 ? weekly.getMae() : weekly.getHorizons().get(h - 1).getMae(),
                                    h == 0 ? direct.getMae() : direct.getHorizons().get(h - 1).getMae(),
                                    h == 0 ? ar.getMae() : ar.getHorizons().get(h - 1).getMae(),
                                    h == 0 ? residual.getMae() : residual.getHorizons().get(h - 1).getMae()},
                            new double[]{h == 0 ? weekly.getRmse() : weekly.getHorizons().get(h - 1).getRmse(),
                                    h == 0 ? direct.getRmse() : direct.getHorizons().get(h - 1).getRmse(),
                                    h == 0 ? ar.getRmse() : ar.getHorizons().get(h - 1).getRmse(),
                                    h == 0 ? residual.getRmse() : residual.getHorizons().get(h - 1).getRmse()}));
                }
            }
        }
        return formatRows(rows);
    }

    static String formatRows(List<Row> rows) {
        StringBuilder out = new StringBuilder("RESIDUAL AR CONTROLLED VALIDATION COMPARISON\n"
                + "Mean seeds within each series, then equal-weight macro across series.\n"
                + "Std below is sample std across series means, not a confidence interval.\n"
                + "Metrics in original sales units; positive improvement is better.\n"
                + "Rolling-origin targets overlap; validation selects checkpoints; no test selection.\n");
        append(out, "OVERALL MACRO", rows, r -> r.horizon == 0);
        for (String stratum : List.of("LOW", "MEDIUM", "HIGH")) {
            append(out, "STRATUM " + stratum, rows, r -> r.horizon == 0 && stratum.equals(r.stratum));
        }
        for (int horizon = 1; horizon <= 7; horizon++) {
            final int h = horizon;
            append(out, "HORIZON " + h, rows, r -> r.horizon == h);
        }
        List<Long> seeds = rows.stream().map(r -> r.seed).distinct().collect(Collectors.toList());
        for (long seed : seeds) {
            append(out, "SEED " + seed, rows, r -> r.horizon == 0 && r.seed == seed);
        }
        out.append("\nACROSS-SEED CONSISTENCY (sample std of per-seed macro)\n");
        for (int model = 0; model < MODELS.size(); model++) {
            List<Double> maes = new ArrayList<>(), rmses = new ArrayList<>();
            for (long seed : seeds) {
                List<Row> subset = rows.stream().filter(r -> r.horizon == 0 && r.seed == seed).collect(Collectors.toList());
                maes.add(stats(subset, model, true).getMean());
                rmses.add(stats(subset, model, false).getMean());
            }
            out.append(String.format(Locale.ROOT, "%s | MAE std %.4f | RMSE std %.4f%n", MODELS.get(model),
                    ExperimentRunner.summarizeValues(maes).getStandardDeviation(),
                    ExperimentRunner.summarizeValues(rmses).getStandardDeviation()));
        }
        return out.toString();
    }

    private static void append(StringBuilder out, String title, List<Row> rows, Predicate<Row> filter) {
        List<Row> subset = rows.stream().filter(filter).collect(Collectors.toList());
        if (subset.isEmpty()) { return; }
        out.append('\n').append(title).append('\n');
        double weeklyMae = stats(subset, 0, true).getMean();
        double weeklyRmse = stats(subset, 0, false).getMean();
        out.append("Model | MAE mean +/- std | RMSE mean +/- std | MAE improvement % | RMSE improvement %\n");
        for (int model = 0; model < MODELS.size(); model++) {
            var mae = stats(subset, model, true);
            var rmse = stats(subset, model, false);
            out.append(String.format(Locale.ROOT, "%s | %.4f +/- %.4f | %.4f +/- %.4f | %s | %s%n",
                    MODELS.get(model), mae.getMean(), mae.getStandardDeviation(), rmse.getMean(), rmse.getStandardDeviation(),
                    improvement(weeklyMae, mae.getMean()), improvement(weeklyRmse, rmse.getMean())));
        }
        var groups = subset.stream().collect(Collectors.groupingBy(Row::seriesKey));
        out.append("Residual RMSE wins (strict, per-series seed mean): ");
        for (int model = 0; model < 3; model++) {
            int wins = 0;
            for (List<Row> group : groups.values()) {
                if (stats(group, 3, false).getMean() < stats(group, model, false).getMean()) { wins++; }
            }
            out.append(MODELS.get(model)).append(' ').append(wins).append('/').append(groups.size()).append("; ");
        }
        out.append('\n');
    }

    private static String improvement(double baseline, double error) {
        return baseline == 0 ? "N/A (zero baseline)"
                : String.format(Locale.ROOT, "%+.2f", ErrorAnalysisRunner.improvementPercent(baseline, error));
    }

    static ExperimentRunner.Stats stats(List<Row> rows, int model, boolean mae) {
        var grouped = rows.stream().collect(Collectors.groupingBy(Row::seriesKey));
        List<Double> means = new ArrayList<>();
        for (List<Row> group : grouped.values()) {
            means.add(group.stream().mapToDouble(r -> (mae ? r.mae : r.rmse)[model]).average().orElseThrow());
        }
        return ExperimentRunner.summarizeValues(means);
    }

    static final class Row {
        final String stratum;
        final int store, item, horizon;
        final long seed;
        final double[] mae, rmse;
        Row(String stratum, int store, int item, long seed, int horizon, double[] mae, double[] rmse) {
            this.stratum = stratum; this.store = store; this.item = item; this.seed = seed;
            this.horizon = horizon; this.mae = mae.clone(); this.rmse = rmse.clone();
        }
        String seriesKey() { return store + ":" + item; }
    }
}
