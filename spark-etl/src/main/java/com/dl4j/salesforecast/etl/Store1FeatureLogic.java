package com.dl4j.salesforecast.etl;

import java.time.LocalDate;

/** Pure Java contract shared by Spark expressions and focused parity tests. */
public final class Store1FeatureLogic {
    public static final String DEFAULT_BRONZE_INPUT = "s3a://bronze/store-1/train.csv";

    private Store1FeatureLogic() { }

    public static String split(LocalDate date) {
        if (!date.isAfter(LocalDate.of(2015, 12, 31))) {
            return "TRAIN";
        }
        if (!date.isAfter(LocalDate.of(2016, 12, 31))) {
            return "VALIDATION";
        }
        return "TEST";
    }

    public static double scale(double value, double trainMin, double trainMax) {
        double scale = trainMax == trainMin ? 1.0 : trainMax - trainMin;
        return (value - trainMin) / scale;
    }

    public static double mean(double... values) {
        if (values.length == 0) {
            throw new IllegalArgumentException("At least one value is required.");
        }
        double sum = 0.0;
        for (double value : values) {
            sum += value;
        }
        return sum / values.length;
    }

    public static double dayOfWeekSin(LocalDate date) {
        return Math.sin(dayAngle(date));
    }

    public static double dayOfWeekCos(LocalDate date) {
        return Math.cos(dayAngle(date));
    }

    private static double dayAngle(LocalDate date) {
        int mondayZero = date.getDayOfWeek().getValue() - 1;
        return 2.0 * Math.PI * mondayZero / 7.0;
    }
}
