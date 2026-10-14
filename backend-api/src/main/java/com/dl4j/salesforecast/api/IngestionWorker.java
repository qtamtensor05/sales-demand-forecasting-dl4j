package com.dl4j.salesforecast.api;

import java.util.Map;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;

@Component
class IngestionWorker {
    private final IngestionRunRepository runs;
    private final ObjectStorage storage;
    private final SalesUploadValidator validator;
    private final SparkSubmitter spark;

    IngestionWorker(IngestionRunRepository runs, ObjectStorage storage,
            SalesUploadValidator validator, SparkSubmitter spark) {
        this.runs = runs; this.storage = storage; this.validator = validator; this.spark = spark;
    }

    @Async("ingestionExecutor")
    public void process(long runId) {
        long started = System.nanoTime();
        try {
            Map<String,Object> run = runs.find(runId);
            String sourceUri = String.valueOf(run.get("sourceUri"));
            String objectName = sourceUri.substring("s3a://bronze/".length());
            SalesUploadValidator.ValidatedCsv csv = validator.validate(storage.get(objectName));
            runs.running(runId);
            SparkSubmitter.Result result = spark.submit(runId, sourceUri,
                    progress -> runs.progress(runId, progress));
            long durationMs = (System.nanoTime() - started) / 1_000_000L;
            if (!result.success()) {
                runs.failed(runId, result.output(), durationMs);
                return;
            }
            runs.publishing(runId);
            runs.publish(runId, csv.rows(), durationMs);
        } catch (Exception failure) {
            runs.failed(runId, failure.getMessage(), (System.nanoTime() - started) / 1_000_000L);
        }
    }
}
