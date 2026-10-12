package com.dl4j.salesforecast.output;

import com.dl4j.salesforecast.model.ErrorAnalysisRunner;
import com.dl4j.salesforecast.model.ExperimentConfiguration;
import com.dl4j.salesforecast.model.ExperimentRunner;
import com.dl4j.salesforecast.model.ResidualComparisonReport;
import com.dl4j.salesforecast.model.MultiSeriesExperimentRunner;
import com.dl4j.salesforecast.model.SeedMetricsExporter;
import com.dl4j.salesforecast.model.TrainingHistoryExporter;
import com.dl4j.salesforecast.model.ValidationPredictionExporter;
import org.nd4j.linalg.factory.Nd4j;

import java.io.IOException;
import java.io.OutputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

/** Owns one immutable run directory and its status manifest. */
public final class RunArtifactSession implements AutoCloseable {
    private static final DateTimeFormatter ID_FORMAT = DateTimeFormatter.ofPattern("yyyy-MM-dd_HHmmss_SSS");
    private final Path directory;
    private final Path manifest;
    private final String runId;
    private final String datasetPath;
    private final String datasetSha256;
    private final Instant startedAt;
    private ExperimentConfiguration configuration;
    private boolean terminal;
    private PrintStream originalOut;
    private PrintStream originalErr;
    private PrintStream logStream;

    private RunArtifactSession(Path directory, String runId, String datasetPath,
                               String datasetSha256, Instant startedAt) {
        this.directory = directory;
        this.manifest = directory.resolve("run.json");
        this.runId = runId;
        this.datasetPath = datasetPath;
        this.datasetSha256 = datasetSha256;
        this.startedAt = startedAt;
    }

    public static RunArtifactSession start(Path runsRoot, Path dataset) throws IOException {
        return start(runsRoot, dataset, ExperimentConfiguration.legacy());
    }

    public static RunArtifactSession start(Path runsRoot, Path dataset, ExperimentConfiguration configuration) throws IOException {
        if (configuration == null) { throw new IllegalArgumentException("Experiment configuration is required."); }
        if (runsRoot == null || dataset == null || !Files.isRegularFile(dataset)) {
            throw new IllegalArgumentException("Runs root and an existing dataset are required.");
        }
        String backend = Nd4j.getBackend().getClass().getSimpleName().toLowerCase();
        String suffix = backend.contains("cuda") || backend.contains("cublas") ? "gpu" : "cpu";
        String runId = ID_FORMAT.format(LocalDateTime.now()) + "_" + suffix;
        Path directory = runsRoot.resolve(runId);
        Files.createDirectories(runsRoot);
        try { Files.createDirectory(directory); }
        catch (java.nio.file.FileAlreadyExistsException collision) {
            runId = runId + "_" + java.util.UUID.randomUUID();
            directory = runsRoot.resolve(runId);
            Files.createDirectory(directory);
        }
        RunArtifactSession session = new RunArtifactSession(directory, runId,
                dataset.toAbsolutePath().normalize().toString(), sha256(dataset), Instant.now());
        session.configuration = configuration;
        session.writeManifest("RUNNING", null);
        try {
            Files.writeString(runsRoot.resolve("latest.txt"), runId + "\n", StandardCharsets.UTF_8);
            session.installConsoleCapture();
        } catch (IOException failure) {
            session.fail(failure);
            session.close();
            throw failure;
        }
        return session;
    }

    public void complete(MultiSeriesExperimentRunner.MultiSeriesResult result,
                         ErrorAnalysisRunner.AnalysisResult analysis) throws IOException {
        if (result.getSeriesResults().size() != configuration.getSeriesCount()
                || !result.getSeeds().equals(configuration.getSeeds())
                || result.getSeriesResults().stream().anyMatch(r -> !r.getSeedResults().stream()
                    .map(ExperimentRunner.SeedResult::getSeed).collect(java.util.stream.Collectors.toList())
                    .equals(configuration.getSeeds()))
                || result.getSeriesResults().stream().flatMap(r -> r.getSeedResults().stream())
                    .anyMatch(r -> (r.getResidual() != null) != configuration.includesResidual()
                            || (r.getResidualHuber() != null) != configuration.isHuberComparison()
                            || (r.getDirectMetrics() != null) == configuration.isHuberComparison()
                            || (r.getAutoregressiveMetrics() != null) == configuration.isHuberComparison())) {
            throw new IllegalArgumentException("Result coverage does not match run configuration.");
        }
        TrainingHistoryExporter.writeCsv(result, directory.resolve("epoch-history.csv"));
        SeedMetricsExporter.writeCsv(result, directory.resolve("seed-metrics.csv"));
        ValidationPredictionExporter.writeCsv(result, directory.resolve("validation-predictions.csv"));
        String errorAnalysis = configuration.includesResidual() ? ResidualComparisonReport.format(result)
                : ErrorAnalysisRunner.formatReport(analysis);
        String warning = configuration.isSmoke() ? "SMOKE ONLY: pipeline verification, not model-quality evidence.\n\n" : "";
        String summary = warning + (configuration.isHuberComparison() ? "" : MultiSeriesExperimentRunner.formatReport(result)) + "\n" + errorAnalysis;
        Files.writeString(directory.resolve("error-analysis.txt"), warning + errorAnalysis, StandardCharsets.UTF_8);
        StringBuilder scales = new StringBuilder("stratum,store,item,train_mean_sales,sales_min,sales_max,sales_scale,residual_scale\n");
        for (var series : result.getSeriesResults()) {
            var scaler = series.getScaler();
            scales.append(String.format(java.util.Locale.ROOT, "%s,%d,%d,%.10f,%.10f,%.10f,%.10f,%.10f%n",
                    series.getStratum(), series.getStoreId(), series.getItemId(), series.getTrainMeanSales(),
                    scaler.getMin(), scaler.getMax(), scaler.getScale(), scaler.getScale()));
        }
        Files.writeString(directory.resolve("series-scaling.csv"), scales, StandardCharsets.UTF_8);
        Files.writeString(directory.resolve("summary.txt"), summary, StandardCharsets.UTF_8);
        Files.writeString(directory.resolve("report.html"), html(summary), StandardCharsets.UTF_8);
        writeManifest("SUCCESS", null);
        terminal = true;
    }

    public void fail(Throwable failure) {
        try {
            writeManifest("FAILED", failure == null ? "Unknown failure" : failure.toString());
            terminal = true;
        } catch (IOException manifestFailure) {
            System.err.println("Cannot mark run FAILED: " + manifestFailure);
        }
    }

    public Path getDirectory() { return directory; }

    private void installConsoleCapture() throws IOException {
        originalOut = System.out;
        originalErr = System.err;
        logStream = new PrintStream(Files.newOutputStream(directory.resolve("console.log")),
                true, StandardCharsets.UTF_8.name());
        System.setOut(new PrintStream(new TeeOutputStream(originalOut, logStream), true,
                StandardCharsets.UTF_8.name()));
        System.setErr(new PrintStream(new TeeOutputStream(originalErr, logStream), true,
                StandardCharsets.UTF_8.name()));
    }

    @Override
    public void close() {
        if (!terminal) { fail(new IllegalStateException("Session closed before completion")); }
        if (originalOut != null) { System.setOut(originalOut); }
        if (originalErr != null) { System.setErr(originalErr); }
        if (logStream != null) { logStream.close(); }
        originalOut = null;
        originalErr = null;
        logStream = null;
    }

    private void writeManifest(String status, String error) throws IOException {
        String backend = Nd4j.getBackend().getClass().getName();
        java.util.Map<String, Object> values = new java.util.LinkedHashMap<>();
        values.put("schemaVersion", configuration.isHuberComparison() ? 3 : configuration.includesResidual() ? 2 : 1);
        values.put("runId", runId);
        values.put("status", status);
        values.put("startedAt", startedAt.toString());
        values.put("updatedAt", Instant.now().toString());
        values.put("javaVersion", System.getProperty("java.version"));
        values.put("backend", backend);
        values.put("datasetPath", datasetPath);
        values.put("datasetSha256", datasetSha256);
        values.put("smoke", configuration.isSmoke());
        values.put("series", configuration.getSeriesCount());
        values.put("seeds", configuration.getSeeds());
        values.put("models", configuration.getModels());
        values.put("baseline", "Weekly Naive");
        values.put("fits", configuration.getFitCount());
        values.put("maxEpochs", configuration.getMaxEpochs());
        values.put("patience", configuration.getPatience());
        values.put("hiddenUnits", ExperimentRunner.DEFAULT_HIDDEN_UNITS);
        values.put("learningRate", ExperimentRunner.DEFAULT_LEARNING_RATE);
        values.put("optimizer", "Adam");
        values.put("loss", configuration.isHuberComparison() ? "MIXED" : "MSE");
        if (configuration.isHuberComparison()) {
            values.put("lossFunction", java.util.Map.of("ResidualAR", "MSE", "ResidualARHuber", "HUBER"));
            values.put("huberDelta", com.dl4j.salesforecast.model.HuberLoss.DELTA);
            values.put("huberDeltaSpace", "scaled residual prediction minus scaled residual label");
            values.put("huberDefinition", "0.5*e^2 for abs(e)<=delta; delta*(abs(e)-0.5*delta) otherwise");
            values.put("checkpointCriterion", "validation H1-H7 rollout RMSE in original sales units");
        }
        values.put("inputDays", 30);
        values.put("forecastDays", 7);
        values.put("features", com.dl4j.salesforecast.preprocessing.SalesPreprocessor.getInputFeatureNames());
        values.put("trainPeriod", "2013-01-01..2015-12-31");
        values.put("selectionSplit", "validation-2016");
        values.put("testUsedForSelection", false);
        values.put("seriesSelection", "4 quantiles per train-only tertile; smoke uses first of same 12");
        values.put("scaling", "Per-series train-only sales MinMax; constant train scale=1; no clipping");
        values.put("residualTarget", configuration.includesResidual()
                ? "(sales(t)-observedSales(t-7))/salesScale; inverse: baseline + output*salesScale" : "disabled");
        values.put("residualScalerFit", false);
        values.put("scalerArtifact", "series-scaling.csv");
        if (error != null) { values.put("error", error); }
        String json = new com.google.gson.GsonBuilder().setPrettyPrinting().create().toJson(values) + "\n";
        Path temporary = manifest.resolveSibling("run.json.tmp");
        Files.writeString(temporary, json, StandardCharsets.UTF_8);
        try {
            Files.move(temporary, manifest, java.nio.file.StandardCopyOption.REPLACE_EXISTING,
                    java.nio.file.StandardCopyOption.ATOMIC_MOVE);
        } catch (java.nio.file.AtomicMoveNotSupportedException unsupported) {
            Files.move(temporary, manifest, java.nio.file.StandardCopyOption.REPLACE_EXISTING);
        }
    }

    private static String html(String summary) {
        return "<!doctype html><html><head><meta charset=\"utf-8\"><title>Validation run report</title>"
                + "<style>body{font-family:system-ui;margin:2rem;max-width:1100px}pre{white-space:pre-wrap;"
                + "background:#f6f8fa;padding:1rem;border-radius:8px}</style></head><body>"
                + "<h1>Validation run report</h1><p>Source tables: epoch-history.csv, seed-metrics.csv, "
                + "validation-predictions.csv.</p><pre>" + escapeHtml(summary) + "</pre></body></html>";
    }

    private static String sha256(Path path) throws IOException {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            try (java.io.InputStream in = Files.newInputStream(path)) {
                byte[] buffer = new byte[65536]; int count;
                while ((count = in.read(buffer)) >= 0) { digest.update(buffer, 0, count); }
            }
            StringBuilder out = new StringBuilder();
            for (byte value : digest.digest()) { out.append(String.format("%02x", value)); }
            return out.toString();
        } catch (java.security.NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 is unavailable.", e);
        }
    }

    private static String escapeHtml(String value) {
        return value.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;");
    }

    private static final class TeeOutputStream extends OutputStream {
        private final OutputStream first;
        private final OutputStream second;
        private TeeOutputStream(OutputStream first, OutputStream second) {
            this.first = first;
            this.second = second;
        }
        @Override public synchronized void write(int value) throws IOException {
            first.write(value); second.write(value);
        }
        @Override public synchronized void write(byte[] data, int offset, int length) throws IOException {
            first.write(data, offset, length); second.write(data, offset, length);
        }
        @Override public synchronized void flush() throws IOException { first.flush(); second.flush(); }
        @Override public void close() throws IOException { flush(); }
    }
}
