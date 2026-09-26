package com.dl4j.salesforecast.preprocessing;

import com.dl4j.salesforecast.analysis.TimeSeriesAnalyzer.TimeSeriesResult;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Objects;

/** Chronological splitting and train-only scaling; no model training. */
public final class SalesPreprocessor {
    private SalesPreprocessor() { }

    /** Defaults for the Store Item Demand Forecasting dataset. */
    public static PreprocessingResult preprocess(TimeSeriesResult series) {
        return preprocess(series, LocalDate.of(2015, 12, 31),
                LocalDate.of(2016, 12, 31), 30, 7);
    }

    /** Split end dates are inclusive. Test includes all remaining observations. */
    public static PreprocessingResult preprocess(TimeSeriesResult series,
                                                LocalDate trainEnd, LocalDate validationEnd,
                                                int inputDays, int forecastDays) {
        Objects.requireNonNull(series, "series");
        Objects.requireNonNull(trainEnd, "trainEnd");
        Objects.requireNonNull(validationEnd, "validationEnd");
        if (!series.isComplete()) {
            throw new IllegalArgumentException("Series must have exactly one record per day; fix gaps/duplicates first.");
        }
        if (inputDays <= 0 || forecastDays <= 0) {
            throw new IllegalArgumentException("Window lengths must be positive.");
        }
        if (trainEnd.isBefore(series.getStartDate()) || !trainEnd.isBefore(validationEnd)
                || !validationEnd.isBefore(series.getEndDate())) {
            throw new IllegalArgumentException("Split dates must define non-empty chronological train/validation/test sets.");
        }
        List<LocalDate> dates = series.getDates();
        List<Double> raw = series.getSales();
        int trainSize = 0;
        int validationLimit = 0;
        for (LocalDate date : dates) {
            if (!date.isAfter(trainEnd)) { trainSize++; }
            if (!date.isAfter(validationEnd)) { validationLimit++; }
        }
        if ((long) trainSize < (long) inputDays + forecastDays
                || validationLimit - trainSize < forecastDays
                || dates.size() - validationLimit < forecastDays) {
            throw new IllegalArgumentException("Each split must contain a full target window; train also needs input history.");
        }

        double min = Double.POSITIVE_INFINITY;
        double max = Double.NEGATIVE_INFINITY;
        for (int i = 0; i < trainSize; i++) {
            min = Math.min(min, raw.get(i));
            max = Math.max(max, raw.get(i));
        }
        MinMaxScaler scaler = new MinMaxScaler(min, max);
        List<Double> normalized = new ArrayList<>();
        for (double value : raw) { normalized.add(scaler.transform(value)); }
        Split train = createSplit("TRAIN", 0, trainSize, dates, raw, normalized, inputDays, forecastDays);
        Split validation = createSplit("VALIDATION", trainSize, validationLimit,
                dates, raw, normalized, inputDays, forecastDays);
        Split test = createSplit("TEST", validationLimit, dates.size(),
                dates, raw, normalized, inputDays, forecastDays);
        return new PreprocessingResult(series.getStoreId(), series.getItemId(), inputDays,
                forecastDays, scaler, train, validation, test);
    }

    private static Split createSplit(String name, int from, int to, List<LocalDate> dates,
                                     List<Double> raw, List<Double> normalized,
                                     int inputDays, int forecastDays) {
        List<Window> windows = new ArrayList<>();
        // Targets stay inside this split; inputs may use earlier observed history.
        for (int target = Math.max(from, inputDays); target <= to - forecastDays; target++) {
            windows.add(new Window(dates.subList(target - inputDays, target),
                    dates.subList(target, target + forecastDays),
                    raw.subList(target - inputDays, target), raw.subList(target, target + forecastDays),
                    normalized.subList(target - inputDays, target),
                    normalized.subList(target, target + forecastDays)));
        }
        return new Split(name, dates.subList(from, to), raw.subList(from, to),
                normalized.subList(from, to), windows);
    }

    public static void printSummary(PreprocessingResult result) {
        System.out.println("\n==========================================\nSALES PREPROCESSING\n==========================================");
        System.out.printf("Store / Item : %d / %d%nWindow       : %d -> %d days%n",
                result.storeId, result.itemId, result.inputDays, result.forecastDays);
        System.out.printf(Locale.ROOT, "Train min/max: %.0f / %.0f%n", result.scaler.min, result.scaler.max);
        System.out.println("Scaler fitted on train only; values are not clipped.");
        System.out.println("Validation/test use observed history before each forecast.");
        for (Split split : List.of(result.train, result.validation, result.test)) {
            System.out.printf("%n%s%nDates   : %s -> %s%nDays    : %d%nWindows : %d%n",
                    split.name, split.dates.get(0), split.dates.get(split.dates.size() - 1),
                    split.dates.size(), split.windows.size());
            Window first = split.windows.get(0);
            Window last = split.windows.get(split.windows.size() - 1);
            System.out.printf("First input  : %s -> %s%nFirst target : %s -> %s%nLast target  : %s -> %s%n",
                    first.inputDates.get(0), first.inputDates.get(first.inputDates.size() - 1),
                    first.targetDates.get(0), first.targetDates.get(first.targetDates.size() - 1),
                    last.targetDates.get(0), last.targetDates.get(last.targetDates.size() - 1));
            printValues("First input (raw -> scaled)", first.rawInput, first.input);
            printValues("First target (raw -> scaled)", first.rawTarget, first.target);
        }
    }

    private static void printValues(String label, List<Double> raw, List<Double> scaled) {
        System.out.println(label);
        for (int i = 0; i < raw.size(); i++) {
            System.out.printf(Locale.ROOT, "  %2d: %6.0f -> %.4f%n", i + 1, raw.get(i), scaled.get(i));
        }
    }

    public static final class MinMaxScaler {
        private final double min;
        private final double max;
        private final double scale;

        private MinMaxScaler(double min, double max) {
            this.min = min;
            this.max = max;
            // Constant training series maps to zero, with a reversible unit scale.
            this.scale = max == min ? 1.0 : max - min;
        }
        public double transform(double value) { return (value - min) / scale; }
        public double inverseTransform(double value) { return value * scale + min; }
        public double getMin() { return min; }
        public double getMax() { return max; }
        public double getScale() { return scale; }
    }

    public static final class Window {
        private final List<LocalDate> inputDates;
        private final List<LocalDate> targetDates;
        private final List<Double> rawInput;
        private final List<Double> rawTarget;
        private final List<Double> input;
        private final List<Double> target;

        private Window(List<LocalDate> inputDates, List<LocalDate> targetDates,
                       List<Double> rawInput, List<Double> rawTarget,
                       List<Double> input, List<Double> target) {
            this.inputDates = List.copyOf(inputDates);
            this.targetDates = List.copyOf(targetDates);
            this.rawInput = List.copyOf(rawInput);
            this.rawTarget = List.copyOf(rawTarget);
            this.input = List.copyOf(input);
            this.target = List.copyOf(target);
        }
        public List<LocalDate> getInputDates() { return inputDates; }
        public List<LocalDate> getTargetDates() { return targetDates; }
        public List<Double> getRawInput() { return rawInput; }
        public List<Double> getRawTarget() { return rawTarget; }
        public List<Double> getInput() { return input; }
        public List<Double> getTarget() { return target; }
    }

    public static final class Split {
        private final String name;
        private final List<LocalDate> dates;
        private final List<Double> rawSales;
        private final List<Double> normalizedSales;
        private final List<Window> windows;

        private Split(String name, List<LocalDate> dates, List<Double> rawSales,
                      List<Double> normalizedSales, List<Window> windows) {
            this.name = name;
            this.dates = List.copyOf(dates);
            this.rawSales = List.copyOf(rawSales);
            this.normalizedSales = List.copyOf(normalizedSales);
            this.windows = List.copyOf(windows);
        }
        public String getName() { return name; }
        public List<LocalDate> getDates() { return dates; }
        public List<Double> getRawSales() { return rawSales; }
        public List<Double> getNormalizedSales() { return normalizedSales; }
        public List<Window> getWindows() { return windows; }
    }

    public static final class PreprocessingResult {
        private final int storeId;
        private final int itemId;
        private final int inputDays;
        private final int forecastDays;
        private final MinMaxScaler scaler;
        private final Split train;
        private final Split validation;
        private final Split test;

        private PreprocessingResult(int storeId, int itemId, int inputDays, int forecastDays,
                                    MinMaxScaler scaler, Split train, Split validation, Split test) {
            this.storeId = storeId;
            this.itemId = itemId;
            this.inputDays = inputDays;
            this.forecastDays = forecastDays;
            this.scaler = scaler;
            this.train = train;
            this.validation = validation;
            this.test = test;
        }
        public int getStoreId() { return storeId; }
        public int getItemId() { return itemId; }
        public int getInputDays() { return inputDays; }
        public int getForecastDays() { return forecastDays; }
        public MinMaxScaler getScaler() { return scaler; }
        public Split getTrain() { return train; }
        public Split getValidation() { return validation; }
        public Split getTest() { return test; }
    }
}
