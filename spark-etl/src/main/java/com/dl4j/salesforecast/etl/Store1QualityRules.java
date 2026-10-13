package com.dl4j.salesforecast.etl;

public final class Store1QualityRules {
    public static final long EXPECTED_ROWS = 91_300;
    public static final long EXPECTED_ITEMS = 50;
    public static final long EXPECTED_TRAIN = 54_750;
    public static final long EXPECTED_VALIDATION = 18_300;
    public static final long EXPECTED_TEST = 18_250;
    public static final long EXPECTED_TRAINING_WINDOWS = 52_300;
    public static final long EXPECTED_VALIDATION_ORIGINS = 18_000;
    public static final long EXPECTED_TEST_ORIGINS = 17_950;

    private Store1QualityRules() { }

    public static void requireExpected(long rows, long items, long duplicates, long missing,
                                       long train, long validation, long test) {
        if (rows != EXPECTED_ROWS || items != EXPECTED_ITEMS || duplicates != 0 || missing != 0
                || train != EXPECTED_TRAIN || validation != EXPECTED_VALIDATION || test != EXPECTED_TEST) {
            throw new IllegalStateException(String.format(
                    "Store 1 quality gate failed: rows=%d items=%d duplicates=%d missing=%d train=%d validation=%d test=%d",
                    rows, items, duplicates, missing, train, validation, test));
        }
    }

    public static void requireExpectedWindows(long trainingWindows, long validationOrigins,
                                              long testOrigins) {
        if (trainingWindows != EXPECTED_TRAINING_WINDOWS
                || validationOrigins != EXPECTED_VALIDATION_ORIGINS
                || testOrigins != EXPECTED_TEST_ORIGINS) {
            throw new IllegalStateException(String.format(
                    "Store 1 window gate failed: training=%d validation=%d test=%d",
                    trainingWindows, validationOrigins, testOrigins));
        }
    }
}
