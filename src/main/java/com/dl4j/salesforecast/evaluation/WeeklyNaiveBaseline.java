package com.dl4j.salesforecast.evaluation;

import com.dl4j.salesforecast.preprocessing.SalesPreprocessor;
import com.dl4j.salesforecast.preprocessing.SalesPreprocessor.Split;
import com.dl4j.salesforecast.preprocessing.SalesPreprocessor.Window;

import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/** Evaluates the seasonal-naive forecast sales(t) = sales(t - 7 days). */
public final class WeeklyNaiveBaseline {
    private static final int SEASON_LENGTH_DAYS = 7;

    private WeeklyNaiveBaseline() { }

    /** Uses observed sales in each split window's history; targets remain in original units. */
    public static BaselineResult evaluate(Split split) {
        if (split == null || split.getWindows().isEmpty()) {
            throw new IllegalArgumentException("Split must contain at least one forecast window.");
        }
        List<Window> windows = split.getWindows();
        int forecastDays = windows.get(0).getRawTarget().size();
        if (forecastDays <= 0 || forecastDays > SEASON_LENGTH_DAYS) {
            throw new IllegalArgumentException("Weekly-naive forecasting supports 1 to 7 forecast days.");
        }
        List<Accumulator> accumulators = new ArrayList<>();
        for (int horizon = 0; horizon < forecastDays; horizon++) {
            accumulators.add(new Accumulator());
        }

        for (Window window : windows) {
            int historyLength = window.getRawInput().size();
            if (historyLength < SEASON_LENGTH_DAYS
                    || window.getInputDates().size() != historyLength
                    || window.getRawInput().size() != historyLength
                    || window.getRawTarget().size() != forecastDays
                    || window.getTarget().size() != forecastDays
                    || window.getTargetDates().size() != forecastDays) {
                throw new IllegalArgumentException("Windows must have aligned dates/sales, a consistent forecast length, "
                        + "and at least 7 days of input history.");
            }
            for (int h = 0; h < forecastDays; h++) {
                long targetOffset = ChronoUnit.DAYS.between(
                        window.getInputDates().get(historyLength - 1), window.getTargetDates().get(h));
                if (targetOffset != h + 1L) {
                    throw new IllegalArgumentException("Forecast target dates must follow the input in daily order.");
                }
                double prediction = window.getRawInput().get(historyLength - SEASON_LENGTH_DAYS + h);
                double actual = window.getRawTarget().get(h);
                accumulators.get(h).add(actual, prediction);
            }
        }

        List<HorizonMetrics> metrics = new ArrayList<>();
        Accumulator overall = new Accumulator();
        for (int h = 0; h < accumulators.size(); h++) {
            Accumulator accumulator = accumulators.get(h);
            metrics.add(new HorizonMetrics(h + 1, accumulator.count,
                    accumulator.meanAbsoluteError(), accumulator.rootMeanSquaredError()));
            overall.combine(accumulator);
        }
        return new BaselineResult(metrics, overall.count,
                overall.meanAbsoluteError(), overall.rootMeanSquaredError());
    }

    /** Evaluate the preprocessor's test split and show metrics by forecast horizon. */
    public static BaselineResult evaluateAndPrint(SalesPreprocessor.PreprocessingResult prepared) {
        if (prepared == null) {
            throw new IllegalArgumentException("Preprocessing result must not be null.");
        }
        BaselineResult result = evaluate(prepared.getTest());
        print(result, prepared.getStoreId(), prepared.getItemId());
        return result;
    }

    public static void print(BaselineResult result, int storeId, int itemId) {
        print(result, storeId, itemId, "TEST");
    }

    public static void print(BaselineResult result, int storeId, int itemId, String splitName) {
        System.out.println("\n==========================================\nWEEKLY NAIVE BASELINE\n==========================================");
        System.out.printf("Store / Item : %d / %d%n", storeId, itemId);
        System.out.println("Rule         : predict sales(t) using observed sales(t - 7 days)");
        System.out.printf("Evaluation   : rolling %s windows; metrics in original sales units%n", splitName);
        System.out.println("Horizon | Forecasts | MAE      | RMSE");
        for (HorizonMetrics metrics : result.horizons) {
            System.out.printf(Locale.ROOT, "%7d | %9d | %8.4f | %.4f%n",
                    metrics.horizonDays, metrics.forecastCount, metrics.mae, metrics.rmse);
        }
        System.out.printf(Locale.ROOT, "Overall | %9d | %8.4f | %.4f%n",
                result.forecastCount, result.mae, result.rmse);
        System.out.println("Each 7-day forecast uses actual observations up to its rolling origin.");
        System.out.println("Overlapping windows repeat target dates at different origins/horizons.");
        System.out.println("==========================================");
    }

    private static final class Accumulator {
        private long count;
        private double absoluteErrorSum;
        private double squaredErrorSum;

        private void add(double actual, double prediction) {
            double error = actual - prediction;
            count++;
            absoluteErrorSum += Math.abs(error);
            squaredErrorSum += error * error;
        }
        private void combine(Accumulator other) {
            count += other.count;
            absoluteErrorSum += other.absoluteErrorSum;
            squaredErrorSum += other.squaredErrorSum;
        }
        private double meanAbsoluteError() { return absoluteErrorSum / count; }
        private double rootMeanSquaredError() { return Math.sqrt(squaredErrorSum / count); }
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

    public static final class BaselineResult {
        private final List<HorizonMetrics> horizons;
        private final long forecastCount;
        private final double mae;
        private final double rmse;

        private BaselineResult(List<HorizonMetrics> horizons, long forecastCount, double mae, double rmse) {
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
}
