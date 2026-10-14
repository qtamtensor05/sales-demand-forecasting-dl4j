package com.dl4j.salesforecast.etl;

import static org.apache.spark.sql.functions.*;

import java.util.HashMap;
import java.util.Map;
import org.apache.spark.sql.Column;
import org.apache.spark.sql.Dataset;
import org.apache.spark.sql.Row;
import org.apache.spark.sql.SaveMode;
import org.apache.spark.sql.SparkSession;
import org.apache.spark.sql.expressions.Window;
import org.apache.spark.sql.expressions.WindowSpec;
import org.apache.spark.sql.types.DataTypes;
import org.apache.spark.sql.types.StructType;

/** Store 1 CSV -> validated Silver Parquet -> causal Gold feature Parquet. */
public final class Store1EtlJob {
    private static final int EXPECTED_DAYS_PER_ITEM = 1_826;

    private Store1EtlJob() { }

    public static void main(String[] args) {
        Map<String, String> options = parseArgs(args);
        String input = options.getOrDefault("input", Store1FeatureLogic.DEFAULT_BRONZE_INPUT);
        String silverOutput = options.getOrDefault("silver", "s3a://silver/store-1");
        String goldOutput = options.getOrDefault("gold", "s3a://gold/store-1");
        String statsOutput = options.getOrDefault("stats", "s3a://benchmarks/etl/store-1-latest");

        SparkSession spark = SparkSession.builder().appName("store-1-sales-etl").getOrCreate();
        spark.sparkContext().setLogLevel("WARN");
        configureMinio(spark);
        long started = System.nanoTime();
        try {
            Dataset<Row> raw = readStore1(spark, input).cache();
            Quality quality = validate(raw);
            Dataset<Row> silver = createSilver(raw).cache();
            WindowCounts windows = calculateWindows(silver);
            silver.repartition(8, col("item")).write().mode(SaveMode.Overwrite).parquet(silverOutput);

            Dataset<Row> gold = createGold(silver).cache();
            long goldRows = gold.count();
            if (goldRows != Store1QualityRules.EXPECTED_ROWS) {
                throw new IllegalStateException("Gold row count mismatch: " + goldRows);
            }
            gold.repartition(8, col("split"), col("item"))
                    .write().mode(SaveMode.Overwrite).partitionBy("split").parquet(goldOutput);

            long durationMs = (System.nanoTime() - started) / 1_000_000L;
            writeStats(raw, statsOutput, quality, windows, goldRows, durationMs);
            System.out.printf("ETL_SUCCESS rows=%d items=%d train=%d validation=%d test=%d trainingWindows=%d validationOrigins=%d testOrigins=%d gold=%d durationMs=%d%n",
                    quality.rows, quality.items, quality.train, quality.validation, quality.test,
                    windows.training, windows.validation, windows.test, goldRows, durationMs);
        } finally {
            spark.stop();
        }
    }

    static Dataset<Row> readStore1(SparkSession spark, String input) {
        StructType schema = new StructType()
                .add("date_text", DataTypes.StringType, false)
                .add("store", DataTypes.IntegerType, false)
                .add("item", DataTypes.IntegerType, false)
                .add("sales", DataTypes.DoubleType, false);
        return spark.read().option("header", true).option("mode", "FAILFAST").schema(schema).csv(input)
                .select(to_date(col("date_text"), "yyyy-MM-dd").alias("date"),
                        col("store"), col("item"), col("sales"))
                .filter(col("store").equalTo(1));
    }

    static Dataset<Row> createSilver(Dataset<Row> raw) {
        Column split = when(col("date").leq(lit("2015-12-31").cast("date")), "TRAIN")
                .when(col("date").leq(lit("2016-12-31").cast("date")), "VALIDATION")
                .otherwise("TEST");
        return raw.withColumn("split", split).orderBy(col("item"), col("date"));
    }

    static Dataset<Row> createGold(Dataset<Row> silver) {
        Dataset<Row> trainBounds = silver.filter(col("split").equalTo("TRAIN"))
                .groupBy("item").agg(min("sales").alias("train_min"), max("sales").alias("train_max"));
        Dataset<Row> joined = silver.join(trainBounds, "item");
        WindowSpec ordered = Window.partitionBy("item").orderBy("date");
        WindowSpec rolling7 = ordered.rowsBetween(-6, 0);
        WindowSpec rolling14 = ordered.rowsBetween(-13, 0);
        Column scale = when(col("train_max").equalTo(col("train_min")), lit(1.0))
                .otherwise(col("train_max").minus(col("train_min")));
        Column mondayZero = pmod(dayofweek(col("date")).plus(5), lit(7));

        Dataset<Row> gold = joined
                .withColumn("sales_lag_7_raw", lag("sales", 7).over(ordered))
                .withColumn("rolling_mean_7_raw", avg("sales").over(rolling7))
                .withColumn("rolling_mean_14_raw", avg("sales").over(rolling14))
                .withColumn("sales_scaled", col("sales").minus(col("train_min")).divide(scale))
                .withColumn("sales_lag_7", col("sales_lag_7_raw").minus(col("train_min")).divide(scale))
                .withColumn("rolling_mean_7", col("rolling_mean_7_raw").minus(col("train_min")).divide(scale))
                .withColumn("rolling_mean_14", col("rolling_mean_14_raw").minus(col("train_min")).divide(scale))
                .withColumn("day_of_week_sin", sin(mondayZero.multiply(2.0 * Math.PI / 7.0)))
                .withColumn("day_of_week_cos", cos(mondayZero.multiply(2.0 * Math.PI / 7.0)))
                .withColumn("feature_ready", row_number().over(ordered).gt(13));
        for (int item = 1; item <= 50; item++) {
            gold = gold.withColumn("item_" + item,
                    when(col("item").equalTo(item), lit(1.0)).otherwise(lit(0.0)));
        }
        return gold.drop("sales_lag_7_raw", "rolling_mean_7_raw", "rolling_mean_14_raw");
    }

    private static Quality validate(Dataset<Row> raw) {
        long rows = raw.count();
        long items = raw.select("item").distinct().count();
        long duplicates = raw.groupBy("store", "item", "date").count().filter(col("count").gt(1)).count();
        long missing = raw.filter(col("date").isNull().or(col("store").isNull())
                .or(col("item").isNull()).or(col("sales").isNull())).count();
        long incompleteSeries = raw.groupBy("item").agg(countDistinct("date").alias("days"))
                .filter(col("days").notEqual(EXPECTED_DAYS_PER_ITEM)).count();
        Dataset<Row> withSplit = createSilver(raw);
        long train = withSplit.filter(col("split").equalTo("TRAIN")).count();
        long validation = withSplit.filter(col("split").equalTo("VALIDATION")).count();
        long test = withSplit.filter(col("split").equalTo("TEST")).count();
        Store1QualityRules.requireExpected(rows, items, duplicates, missing, train, validation, test);
        if (incompleteSeries != 0) {
            throw new IllegalStateException("Store 1 quality gate failed: incomplete item series=" + incompleteSeries);
        }
        return new Quality(rows, items, train, validation, test);
    }

    private static WindowCounts calculateWindows(Dataset<Row> silver) {
        long training = 0;
        long validation = 0;
        long test = 0;
        for (Row row : silver.groupBy("item", "split").count().collectAsList()) {
            String split = row.getAs("split");
            long count = row.getLong(2);
            if ("TRAIN".equals(split)) {
                training += Math.max(0, count - 49); // 30 input + 13 feature warm-up + 7 target - 1
            } else if ("VALIDATION".equals(split)) {
                validation += Math.max(0, count - 6);
            } else if ("TEST".equals(split)) {
                test += Math.max(0, count - 6);
            }
        }
        Store1QualityRules.requireExpectedWindows(training, validation, test);
        return new WindowCounts(training, validation, test);
    }

    private static void writeStats(Dataset<Row> raw, String output, Quality quality, WindowCounts windows,
                                   long goldRows, long durationMs) {
        raw.limit(1).select(
                lit("SUCCESS").alias("status"), lit(quality.rows).alias("rawRows"),
                lit(quality.items).alias("items"), lit(quality.train).alias("trainRows"),
                lit(quality.validation).alias("validationRows"), lit(quality.test).alias("testRows"),
                lit(windows.training).alias("trainingWindows"),
                lit(windows.validation).alias("validationOrigins"),
                lit(windows.test).alias("testOrigins"),
                lit(goldRows).alias("goldRows"), lit(durationMs).alias("durationMs"),
                current_timestamp().alias("completedAtUtc"))
                .coalesce(1).write().mode(SaveMode.Overwrite).json(output);
    }

    static void configureMinio(SparkSession spark) {
        String access = System.getenv().getOrDefault("MINIO_ROOT_USER", "salesadmin");
        String secret = System.getenv().getOrDefault("MINIO_ROOT_PASSWORD", "change-me-minio-password");
        var hadoop = spark.sparkContext().hadoopConfiguration();
        hadoop.set("fs.s3a.endpoint", System.getenv().getOrDefault("MINIO_ENDPOINT", "http://minio:9000"));
        hadoop.set("fs.s3a.access.key", access);
        hadoop.set("fs.s3a.secret.key", secret);
        hadoop.set("fs.s3a.path.style.access", "true");
        hadoop.set("fs.s3a.connection.ssl.enabled", "false");
        hadoop.set("fs.s3a.impl", "org.apache.hadoop.fs.s3a.S3AFileSystem");
    }

    private static Map<String, String> parseArgs(String[] args) {
        if (args.length % 2 != 0) {
            throw new IllegalArgumentException("Arguments must be --name value pairs.");
        }
        Map<String, String> parsed = new HashMap<>();
        for (int i = 0; i < args.length; i += 2) {
            if (!args[i].startsWith("--")) {
                throw new IllegalArgumentException("Unknown argument: " + args[i]);
            }
            parsed.put(args[i].substring(2), args[i + 1]);
        }
        return parsed;
    }

    private static final class Quality {
        private final long rows;
        private final long items;
        private final long train;
        private final long validation;
        private final long test;

        private Quality(long rows, long items, long train, long validation, long test) {
            this.rows = rows;
            this.items = items;
            this.train = train;
            this.validation = validation;
            this.test = test;
        }
    }

    private static final class WindowCounts {
        private final long training;
        private final long validation;
        private final long test;

        private WindowCounts(long training, long validation, long test) {
            this.training = training;
            this.validation = validation;
            this.test = test;
        }
    }
}
