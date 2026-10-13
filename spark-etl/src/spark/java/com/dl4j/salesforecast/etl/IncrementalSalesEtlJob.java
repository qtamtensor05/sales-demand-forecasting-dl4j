package com.dl4j.salesforecast.etl;

import static org.apache.spark.sql.functions.*;

import java.util.HashMap;
import java.util.Map;
import org.apache.spark.sql.Dataset;
import org.apache.spark.sql.Row;
import org.apache.spark.sql.SaveMode;
import org.apache.spark.sql.SparkSession;
import org.apache.spark.sql.types.DataTypes;
import org.apache.spark.sql.types.StructType;

/** Run-scoped ingestion ETL. It never trains or updates a forecasting model. */
public final class IncrementalSalesEtlJob {
    private IncrementalSalesEtlJob() { }

    public static void main(String[] args) {
        Map<String,String> options = parseArgs(args);
        String runId = required(options, "run-id");
        String input = required(options, "input");
        String silver = options.getOrDefault("silver", "s3a://silver/ingestion/run-" + runId);
        String gold = options.getOrDefault("gold", "s3a://gold/ingestion/run-" + runId);
        String stats = options.getOrDefault("stats", "s3a://benchmarks/ingestion/run-" + runId);
        SparkSession spark = SparkSession.builder().appName("sales-ingestion-" + runId).getOrCreate();
        spark.sparkContext().setLogLevel("WARN");
        Store1EtlJob.configureMinio(spark);
        long started = System.nanoTime();
        try {
            StructType schema = new StructType().add("date_text", DataTypes.StringType, false)
                    .add("store", DataTypes.IntegerType, false).add("item", DataTypes.IntegerType, false)
                    .add("sales", DataTypes.IntegerType, false);
            Dataset<Row> raw = spark.read().option("header", true).option("mode", "FAILFAST")
                    .schema(schema).csv(input).select(to_date(col("date_text"), "yyyy-MM-dd").alias("date"),
                            col("store"), col("item"), col("sales")).cache();
            long rows = raw.count();
            if (rows == 0) throw new IllegalStateException("CSV has no data rows");
            long invalid = raw.filter(col("date").isNull().or(col("store").leq(0))
                    .or(col("item").leq(0)).or(col("sales").lt(0))).count();
            long duplicates = raw.groupBy("date", "store", "item").count().filter(col("count").gt(1)).count();
            if (invalid != 0 || duplicates != 0) {
                throw new IllegalStateException("Quality gate failed: invalid=" + invalid + ", duplicates=" + duplicates);
            }
            progress("SPARK_READ", rows, rows);
            raw.orderBy("store", "item", "date").write().mode(SaveMode.Overwrite).parquet(silver);
            progress("WRITE_SILVER_GOLD", rows / 2, rows);
            Dataset<Row> enriched = raw.withColumn("year", year(col("date")))
                    .withColumn("month", month(col("date"))).withColumn("week", weekofyear(col("date")))
                    .withColumn("day_of_week", dayofweek(col("date")));
            enriched.repartition(col("store"), col("item")).write().mode(SaveMode.Overwrite).parquet(gold);
            progress("WRITE_SILVER_GOLD", rows, rows);
            long duration = (System.nanoTime() - started) / 1_000_000L;
            enriched.limit(1).select(lit("SUCCESS").alias("status"), lit(runId).alias("runId"),
                    lit(rows).alias("rawRows"), lit(rows).alias("goldRows"), lit(duration).alias("durationMs"),
                    current_timestamp().alias("completedAtUtc")).coalesce(1).write().mode(SaveMode.Overwrite).json(stats);
            System.out.printf("INGESTION_ETL_SUCCESS runId=%s raw=%d gold=%d durationMs=%d%n", runId, rows, rows, duration);
        } finally {
            spark.stop();
        }
    }

    private static void progress(String stage, long completed, long total) {
        System.out.printf("INGESTION_PROGRESS stage=%s completed=%d total=%d%n", stage, completed, total);
        System.out.flush();
    }

    private static String required(Map<String,String> options, String name) {
        String value = options.get(name);
        if (value == null || value.isBlank()) throw new IllegalArgumentException("Missing --" + name);
        return value;
    }

    private static Map<String,String> parseArgs(String[] args) {
        if (args.length % 2 != 0) throw new IllegalArgumentException("Arguments must be --name value pairs");
        Map<String,String> result = new HashMap<>();
        for (int i = 0; i < args.length; i += 2) {
            if (!args[i].startsWith("--")) throw new IllegalArgumentException("Unknown argument " + args[i]);
            result.put(args[i].substring(2), args[i + 1]);
        }
        return result;
    }
}
