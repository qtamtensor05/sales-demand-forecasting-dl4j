package com.dl4j.salesforecast.api;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Component
class SparkSubmitter {
    private static final Pattern PROGRESS = Pattern.compile(
            "^INGESTION_PROGRESS stage=([A-Z_]+) completed=(\\d+) total=(\\d+)$");
    private final String executable;
    private final String master;
    private final String jar;
    private final long timeoutSeconds;

    SparkSubmitter(@Value("${ingestion.spark-submit:/opt/spark/bin/spark-submit}") String executable,
            @Value("${ingestion.spark-master:spark://spark-master:7077}") String master,
            @Value("${ingestion.spark-jar:/opt/spark-jobs/spark-etl.jar}") String jar,
            @Value("${ingestion.spark-timeout-seconds:600}") long timeoutSeconds) {
        this.executable = executable; this.master = master; this.jar = jar; this.timeoutSeconds = timeoutSeconds;
    }

    Result submit(long runId, String sourceUri, Consumer<Progress> progressConsumer) {
        List<String> command = new ArrayList<>();
        command.add(executable); command.add("--master"); command.add(master);
        command.add("--class"); command.add("com.dl4j.salesforecast.etl.IncrementalSalesEtlJob");
        command.add(jar); command.add("--run-id"); command.add(Long.toString(runId));
        command.add("--input"); command.add(sourceUri);
        Process process = null;
        try {
            process = new ProcessBuilder(command).redirectErrorStream(true).start();
            StringBuilder output = new StringBuilder();
            Process running = process;
            Thread reader = new Thread(() -> readOutput(running, output, progressConsumer), "spark-progress-" + runId);
            reader.setDaemon(true);
            reader.start();
            if (!process.waitFor(timeoutSeconds, TimeUnit.SECONDS)) {
                process.destroyForcibly();
                return new Result(false, "Spark quá thời gian " + timeoutSeconds + " giây");
            }
            reader.join(2000);
            return new Result(process.exitValue() == 0, trim(output.toString()));
        } catch (Exception failure) {
            if (process != null) process.destroyForcibly();
            return new Result(false, failure.getMessage());
        }
    }

    private void readOutput(Process process, StringBuilder output, Consumer<Progress> consumer) {
        try (BufferedReader lines = new BufferedReader(new InputStreamReader(process.getInputStream(), StandardCharsets.UTF_8))) {
            String line;
            while ((line = lines.readLine()) != null) {
                if (output.length() > 12000) output.delete(0, output.length() - 8000);
                output.append(line).append(System.lineSeparator());
                parseProgress(line).ifPresent(consumer);
            }
        } catch (Exception ignored) { }
    }

    static Optional<Progress> parseProgress(String line) {
        Matcher matcher = PROGRESS.matcher(line == null ? "" : line.trim());
        if (!matcher.matches()) return Optional.empty();
        return Optional.of(new Progress(matcher.group(1), Long.parseLong(matcher.group(2)),
                Long.parseLong(matcher.group(3))));
    }

    private String trim(String output) {
        String value = output == null ? "" : output.trim();
        return value.length() <= 4000 ? value : value.substring(value.length() - 4000);
    }

    static final class Result {
        private final boolean success; private final String output;
        Result(boolean success, String output) { this.success = success; this.output = output; }
        boolean success() { return success; } String output() { return output; }
    }

    static final class Progress {
        private final String stage; private final long completedRows; private final long totalRows;
        Progress(String stage, long completedRows, long totalRows) {
            this.stage = stage; this.completedRows = completedRows; this.totalRows = totalRows;
        }
        String stage() { return stage; }
        long completedRows() { return completedRows; }
        long totalRows() { return totalRows; }
    }
}
