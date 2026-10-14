package com.dl4j.salesforecast.etl;

import static org.apache.spark.sql.functions.col;

import java.sql.Date;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import org.apache.spark.sql.Dataset;
import org.apache.spark.sql.Row;
import org.apache.spark.sql.SparkSession;

/** Runtime parity check between persisted Spark Gold features and the Java feature contract. */
public final class Store1ParityJob {
    private static final double TOLERANCE = 1e-9;
    private static final List<LocalDate> CHECK_DATES = List.of(
            LocalDate.of(2013, 1, 14), LocalDate.of(2015, 12, 31),
            LocalDate.of(2016, 1, 1), LocalDate.of(2017, 12, 31));

    private Store1ParityJob() { }

    public static void main(String[] args) {
        SparkSession spark = SparkSession.builder().appName("store-1-feature-parity").getOrCreate();
        spark.sparkContext().setLogLevel("WARN");
        Store1EtlJob.configureMinio(spark);
        int checked = 0;
        try {
            Dataset<Row> raw = Store1EtlJob.readStore1(spark, Store1FeatureLogic.DEFAULT_BRONZE_INPUT);
            Dataset<Row> gold = spark.read().parquet("s3a://gold/store-1");
            for (int item : new int[] {1, 25, 50}) {
                List<Row> source = raw.filter(col("item").equalTo(item)).orderBy("date").collectAsList();
                List<Double> sales = new ArrayList<>(source.size());
                List<LocalDate> dates = new ArrayList<>(source.size());
                for (Row row : source) {
                    dates.add(row.getAs("date") instanceof Date
                            ? ((Date) row.getAs("date")).toLocalDate()
                            : LocalDate.parse(row.getAs("date").toString()));
                    sales.add(((Number) row.getAs("sales")).doubleValue());
                }
                double trainMin = sales.subList(0, 1_095).stream().mapToDouble(Double::doubleValue).min().orElseThrow();
                double trainMax = sales.subList(0, 1_095).stream().mapToDouble(Double::doubleValue).max().orElseThrow();
                for (LocalDate date : CHECK_DATES) {
                    int index = dates.indexOf(date);
                    Row actual = gold.filter(col("item").equalTo(item)
                                    .and(col("date").equalTo(Date.valueOf(date))))
                            .select("split", "sales_scaled", "sales_lag_7", "rolling_mean_7",
                                    "rolling_mean_14", "day_of_week_sin", "day_of_week_cos",
                                    "feature_ready", "item_1", "item_25", "item_50")
                            .head();
                    requireEqual("split", Store1FeatureLogic.split(date), actual.getString(0));
                    requireClose("sales", Store1FeatureLogic.scale(sales.get(index), trainMin, trainMax), actual.getDouble(1));
                    requireClose("lag7", Store1FeatureLogic.scale(sales.get(index - 7), trainMin, trainMax), actual.getDouble(2));
                    requireClose("rolling7", Store1FeatureLogic.scale(mean(sales, index - 6, index + 1), trainMin, trainMax), actual.getDouble(3));
                    requireClose("rolling14", Store1FeatureLogic.scale(mean(sales, index - 13, index + 1), trainMin, trainMax), actual.getDouble(4));
                    requireClose("dowSin", Store1FeatureLogic.dayOfWeekSin(date), actual.getDouble(5));
                    requireClose("dowCos", Store1FeatureLogic.dayOfWeekCos(date), actual.getDouble(6));
                    if (!actual.getBoolean(7)) {
                        throw new IllegalStateException("feature_ready=false for " + item + "/" + date);
                    }
                    requireClose("item_1", item == 1 ? 1.0 : 0.0, actual.getDouble(8));
                    requireClose("item_25", item == 25 ? 1.0 : 0.0, actual.getDouble(9));
                    requireClose("item_50", item == 50 ? 1.0 : 0.0, actual.getDouble(10));
                    checked++;
                }
            }
            System.out.printf("PARITY_SUCCESS rows=%d dynamicFeatures=6 identityChannels=50 tolerance=%g%n",
                    checked, TOLERANCE);
        } finally {
            spark.stop();
        }
    }

    private static double mean(List<Double> values, int from, int to) {
        double sum = 0.0;
        for (int i = from; i < to; i++) {
            sum += values.get(i);
        }
        return sum / (to - from);
    }

    private static void requireClose(String name, double expected, double actual) {
        if (!Double.isFinite(actual) || Math.abs(expected - actual) > TOLERANCE) {
            throw new IllegalStateException(name + " parity failed: expected=" + expected + " actual=" + actual);
        }
    }

    private static void requireEqual(String name, String expected, String actual) {
        if (!expected.equals(actual)) {
            throw new IllegalStateException(name + " parity failed: expected=" + expected + " actual=" + actual);
        }
    }
}
